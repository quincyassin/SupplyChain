import {
  memo,
  startTransition,
  useCallback,
  useDeferredValue,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
} from "react";
import {
  Alert,
  Badge,
  Button,
  Checkbox,
  DatePicker,
  Descriptions,
  Empty,
  Input,
  List,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from "antd";
import dayjs, { Dayjs } from "dayjs";
import type { Key } from "antd/es/table/interface";
import {
  DeleteOutlined,
  DownloadOutlined,
  PlusOutlined,
  ScissorOutlined,
  SearchOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import type { TablePaginationConfig } from "antd/es/table/interface";
import type { InputRef } from "antd/es/input";
import type { TextAreaRef } from "antd/es/input/TextArea";
import {
  assignPendingMerchantsForRange,
  batchUpdateReceipt,
  ReceiptStatus,
  importOrdersByPlatform,
  previewImportDuplicates,
  hasPendingMerchantSplit,
  type ColumnMappingItem,
  type ImportDuplicatePreview,
  type ImportDuplicateRow,
  openSplitExportDirectory,
  SplitResult,
  SplitTableRow,
  downloadBlob,
  downloadReceiptByMerchantExport,
  downloadSplitByMerchantExport,
  exportReceiptByMerchant,
  exportSelectedOrders,
  openReceiptExportDirectory,
  deleteImportedOrder,
  deleteSelectedImportedOrders,
  fetchImportedDateSummaries,
  fetchImportedOrdersByDateRange,
  fetchPlatformTemplates,
  formatExportDateFolderPath,
  formatLocalDateKey,
  ImportedDateSummary,
  MerchantSplitGroup,
  PENDING_SPLIT_MERCHANT,
  PlatformSummary,
  markImportedOrderAfterSales,
  cancelImportedOrderAfterSales,
  readExcelHeaders,
  updateImportedOrderFields,
  type EditableOrderFieldKey,
  updateImportedOrderMerchant,
} from "../api/orderApi";
import { useResizableColumns } from "../hooks/useResizableColumns";
import { useTableBodyScrollY } from "../hooks/useTableBodyScrollY";

const { RangePicker } = DatePicker;

interface UploadPanelProps {
  onProcessed?: () => void;
}

/** 与后端 ImportOrderQueryService.UNKNOWN_PLATFORM 一致 */
const UNKNOWN_PLATFORM = "未记录平台";

/** 外层平台 Tab：全部平台 */
const ALL_PLATFORM_TAB_KEY = "__ALL__";

/** 内层商家 Tab：全部商家 */
const ALL_MERCHANT_TAB_KEY = "__ALL_MERCHANT__";

/** 回单状态筛选：全部 */
const ALL_RECEIPT_FILTER_KEY = "__ALL__";

/** 空值占位符样式：售后行着色时不跟随变红/绿 */
const ORDER_TABLE_EMPTY_HINT_CLASS = "order-table-empty-hint";

const ORDER_TABLE_RECEIPT_STATUS_CELL_CLASS = "order-table-col-receipt-status";
const ORDER_TABLE_ACTION_CELL_CLASS = "order-table-col-action";

const RECEIPT_PLACEHOLDER =
  "每行一条，包含系统单号、物流单号、物流公司即可，顺序不限\n同一单号可填多个物流单号，英文或中文逗号分隔\n例如：\n5205061632  韵达  单号1，单号2\n0123456789  SF1234567890  顺丰";

interface DateRangeKey {
  start: string;
  end: string;
}

function createTodayDateRange(): DateRangeKey {
  const today = formatLocalDateKey();
  return { start: today, end: today };
}

interface PlatformFilterItem {
  platform: string;
  rowCount: number;
  receiptedCount: number;
}

interface MerchantSplitTargetItem {
  merchant: string;
  rowCount: number;
}

function buildMerchantSplitExportHint(filterPlatform: string | null): string {
  if (filterPlatform == null) {
    return "将对日期区间内全部订单执行商家分单（已有商家不变），并导出以下全部商家：";
  }
  return "将对日期区间内全部订单执行商家分单（已有商家不变），并导出当前选中平台下全部商家：";
}

function formatReceiptCountSummary(
  receiptedCount: number,
  rowCount: number,
): string {
  return `${receiptedCount}/${rowCount}`;
}

function resolveRowReceiptStatus(row: SplitTableRow): ReceiptStatus {
  return row.receiptStatus ?? "PENDING";
}

function resolveAfterSalesRowClassName(row: SplitTableRow): string {
  const status = row.afterSalesStatus;
  if (status === "COMPLETED") {
    return "after-sales-row-completed";
  }
  if (status === "PENDING" || row.afterSales === true) {
    return "after-sales-row-pending";
  }
  return "";
}

function patchSplitResultAfterSalesRow(
  result: SplitResult,
  systemNo: string,
  patch: Partial<SplitTableRow>,
): SplitResult {
  return {
    ...result,
    pageRows: (result.pageRows ?? []).map((row) =>
      row.systemNo === systemNo ? { ...row, ...patch } : row,
    ),
  };
}

function mergeOrderRowsIntoDataset(
  dataset: SplitResult,
  updatedRows: SplitTableRow[],
): SplitResult {
  if (updatedRows.length === 0) {
    return dataset;
  }
  const updatedBySystemNo = new Map(
    updatedRows
      .filter((row) => Boolean(row.systemNo))
      .map((row) => [row.systemNo as string, row]),
  );
  return {
    ...dataset,
    pageRows: (dataset.pageRows ?? []).map((row) =>
      row.systemNo && updatedBySystemNo.has(row.systemNo)
        ? updatedBySystemNo.get(row.systemNo)!
        : row,
    ),
  };
}

function removeOrderRowsFromDataset(
  dataset: SplitResult,
  systemNos: Set<string>,
): SplitResult {
  return {
    ...dataset,
    pageRows: (dataset.pageRows ?? []).filter(
      (row) => !row.systemNo || !systemNos.has(row.systemNo),
    ),
  };
}

function propagateProductPriceInDataset(
  dataset: SplitResult,
  sourceRow: SplitTableRow,
  fieldKey: ProductPriceFieldKey,
  price: number,
): SplitResult {
  const productName = sourceRow.productName?.trim() ?? "";
  const spec = sourceRow.spec?.trim() ?? "";
  const platform = resolveRowPlatformName(sourceRow.platform);
  return {
    ...dataset,
    pageRows: (dataset.pageRows ?? []).map((row) => {
      if ((row.productName?.trim() ?? "") !== productName) {
        return row;
      }
      if ((row.spec?.trim() ?? "") !== spec) {
        return row;
      }
      if (
        fieldKey === "supplyPrice" &&
        resolveRowPlatformName(row.platform) !== platform
      ) {
        return row;
      }
      return { ...row, [fieldKey]: price };
    }),
  };
}

function buildEditableFieldPatch(
  fieldKey: EditableOrderFieldKey,
  value: string,
  currentRow?: SplitTableRow,
): Partial<SplitTableRow> {
  const patch: Partial<SplitTableRow> = { [fieldKey]: value };
  if (fieldKey === "logisticsNo" || fieldKey === "logisticsCompany") {
    const logisticsNo =
      fieldKey === "logisticsNo" ? value : currentRow?.logisticsNo;
    const logisticsCompany =
      fieldKey === "logisticsCompany" ? value : currentRow?.logisticsCompany;
    if (logisticsNo?.trim() && logisticsCompany?.trim()) {
      patch.receiptStatus = "RECEIPTED";
      patch.receiptStatusLabel = "已回单";
    }
  }
  return patch;
}

type OrderCellUpdatedContext = {
  patch?: Partial<SplitTableRow>;
  productPrice?: {
    fieldKey: ProductPriceFieldKey;
    price: number;
  };
};

function resolveRowPlatformName(platform: string | undefined): string {
  const trimmed = platform?.trim();
  return trimmed ? trimmed : UNKNOWN_PLATFORM;
}

function resolveRowMerchantName(merchant: string | undefined): string {
  const trimmed = merchant?.trim();
  return trimmed ? trimmed : PENDING_SPLIT_MERCHANT;
}

function filterRowsByPlatform(
  rows: SplitTableRow[],
  filterPlatform: string | null,
): SplitTableRow[] {
  if (filterPlatform == null) {
    return rows;
  }
  return rows.filter(
    (row) => resolveRowPlatformName(row.platform) === filterPlatform,
  );
}

function filterRowsByMerchant(
  rows: SplitTableRow[],
  activeMerchant: string,
): SplitTableRow[] {
  if (activeMerchant === ALL_MERCHANT_TAB_KEY || activeMerchant === "") {
    return rows;
  }
  return rows.filter(
    (row) => resolveRowMerchantName(row.merchant) === activeMerchant,
  );
}

function summarizeMerchantGroups(rows: SplitTableRow[]): MerchantSplitGroup[] {
  const countByMerchant = new Map<
    string,
    { rowCount: number; receiptedCount: number }
  >();
  for (const row of rows) {
    const merchant = resolveRowMerchantName(row.merchant);
    const counts = countByMerchant.get(merchant) ?? {
      rowCount: 0,
      receiptedCount: 0,
    };
    counts.rowCount += 1;
    if (resolveRowReceiptStatus(row) === "RECEIPTED") {
      counts.receiptedCount += 1;
    }
    countByMerchant.set(merchant, counts);
  }
  return Array.from(countByMerchant.entries())
    .sort(([left], [right]) => left.localeCompare(right, "zh-CN"))
    .map(([merchant, counts]) => ({
      merchant,
      rowCount: counts.rowCount,
      receiptedCount: counts.receiptedCount,
      rows: [],
    }));
}

function summarizePlatformSummaries(rows: SplitTableRow[]): PlatformSummary[] {
  const countByPlatform = new Map<
    string,
    { rowCount: number; receiptedCount: number }
  >();
  for (const row of rows) {
    const platform = resolveRowPlatformName(row.platform);
    const counts = countByPlatform.get(platform) ?? {
      rowCount: 0,
      receiptedCount: 0,
    };
    counts.rowCount += 1;
    if (resolveRowReceiptStatus(row) === "RECEIPTED") {
      counts.receiptedCount += 1;
    }
    countByPlatform.set(platform, counts);
  }
  return Array.from(countByPlatform.entries())
    .sort(([left], [right]) => left.localeCompare(right, "zh-CN"))
    .map(([platform, counts]) => ({
      platform,
      rowCount: counts.rowCount,
      receiptedCount: counts.receiptedCount,
    }));
}

/** 在前端按平台/商家 Tab 过滤，避免切换 Tab 重复请求后端 */
function deriveSplitResultView(
  base: SplitResult,
  filterPlatform: string | null,
  activeMerchant: string,
): SplitResult {
  const allRows = base.pageRows ?? [];
  const platformScopedRows = filterRowsByPlatform(allRows, filterPlatform);
  const pageRows = filterRowsByMerchant(platformScopedRows, activeMerchant);
  const platformSummaries = summarizePlatformSummaries(allRows);
  const merchantGroups = summarizeMerchantGroups(platformScopedRows);
  return {
    ...base,
    pageRows,
    totalRows: pageRows.length,
    platformSummaries,
    platformCount: platformSummaries.length,
    merchantGroups,
    merchantCount: countRealMerchants(merchantGroups),
  };
}

function resolveRowIssueDateKey(row: SplitTableRow, fallback: string): string {
  const raw = row.issueDate?.trim();
  if (raw && raw.length >= 10) {
    return raw.slice(0, 10);
  }
  return fallback;
}

/** 分单日期展示：仅 yyyy-MM-dd，去掉时分秒 */
function formatIssueDateDisplay(value: unknown): string {
  if (value == null) {
    return "—";
  }
  const text = String(value).trim();
  if (!text) {
    return "—";
  }
  if (text.length >= 10) {
    return text.slice(0, 10);
  }
  return text;
}

function isDateKeyInRange(dateKey: string, range: DateRangeKey): boolean {
  return dateKey >= range.start && dateKey <= range.end;
}

function pickDefaultMerchant(
  groups: Array<{ merchant: string; rowCount?: number }>,
  prev: string,
): string {
  if (groups.length === 0) {
    return "";
  }
  if (prev === ALL_MERCHANT_TAB_KEY) {
    return ALL_MERCHANT_TAB_KEY;
  }
  if (prev !== "" && groups.some((group) => group.merchant === prev)) {
    return prev;
  }
  return ALL_MERCHANT_TAB_KEY;
}

/** 首页商家 Tab：仅展示当前平台筛选下有订单的商家 */
function buildMerchantGroupsForTabs(
  merchantGroups: MerchantSplitGroup[],
): MerchantSplitGroup[] {
  const groups: MerchantSplitGroup[] = [];
  const pendingGroup = merchantGroups.find(
    (group) => group.merchant === PENDING_SPLIT_MERCHANT,
  );
  if (pendingGroup != null && (pendingGroup.rowCount ?? 0) > 0) {
    groups.push({
      merchant: PENDING_SPLIT_MERCHANT,
      rowCount: pendingGroup.rowCount ?? 0,
      receiptedCount: pendingGroup.receiptedCount ?? 0,
      rows: [],
    });
  }
  for (const group of merchantGroups) {
    if (group.merchant === PENDING_SPLIT_MERCHANT) {
      continue;
    }
    if ((group.rowCount ?? 0) <= 0) {
      continue;
    }
    groups.push({
      merchant: group.merchant,
      rowCount: group.rowCount ?? 0,
      receiptedCount: group.receiptedCount ?? 0,
      rows: [],
    });
  }
  return groups;
}

function merchantHasRowsUnderPlatform(
  rows: SplitTableRow[],
  platform: string | null,
  merchant: string,
): boolean {
  if (merchant === ALL_MERCHANT_TAB_KEY || merchant === "") {
    return true;
  }
  return filterRowsByPlatform(rows, platform).some(
    (row) => resolveRowMerchantName(row.merchant) === merchant,
  );
}

function isSelectableSplitDate(value: Dayjs): boolean {
  const day = value.startOf("day");
  const today = dayjs().startOf("day");
  return !day.isAfter(today);
}

function rowKeyOf(row: SplitTableRow, merchant: string): string {
  if (row.systemNo) {
    return row.systemNo;
  }
  return `${merchant}-${row.orderNo}-${row.issueDate}`;
}

function formatCellText(value: unknown): string {
  if (value == null) {
    return "—";
  }
  const text = String(value).trim();
  return text || "—";
}

function formatShippingFee(value: number | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return "—";
  }
  return value.toFixed(2);
}

function formatProductPrice(value: number | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return "—";
  }
  return value.toFixed(2);
}

function resolveNumericAmount(value: number | undefined): number {
  if (value == null || Number.isNaN(value)) {
    return 0;
  }
  return value;
}

function formatMoneyAmount(value: number): string {
  return value.toFixed(2);
}

function formatSummarySingleDateLabel(dateKey: string): string {
  const today = formatLocalDateKey();
  if (dateKey === today) {
    return `${dateKey}（今天）`;
  }
  return dateKey;
}

function formatSummaryDateLabel(
  range: DateRangeKey,
  singleDay: boolean,
): string {
  if (singleDay || range.start === range.end) {
    return formatSummarySingleDateLabel(range.start);
  }
  return `${range.start} ~ ${range.end}`;
}

interface SidebarFinancialSummary {
  revenue: number;
  cost: number;
  profit: number;
}

function summarizeSidebarFinancials(
  rows: SplitTableRow[],
): SidebarFinancialSummary {
  let revenue = 0;
  let cost = 0;
  let profit = 0;
  for (const row of rows) {
    const quantity = resolveNumericAmount(row.quantity);
    const shippingFee = resolveNumericAmount(row.shippingFee);
    const supplyPrice = resolveNumericAmount(row.supplyPrice);
    const costPrice = resolveNumericAmount(row.costPrice);
    revenue += supplyPrice * quantity + shippingFee;
    cost += costPrice * quantity + shippingFee;
    if (supplyPrice > 0 && costPrice > 0) {
      profit += (supplyPrice - costPrice) * quantity;
    }
  }
  return { revenue, cost, profit };
}

function countRealMerchants(groups: MerchantSplitGroup[]): number {
  return groups.filter((group) => group.merchant !== PENDING_SPLIT_MERCHANT)
    .length;
}

function resolveOrderDetailItems(row: SplitTableRow) {
  return [
    { label: "系统编号", value: formatCellText(row.systemNo) },
    { label: "订单编号", value: formatCellText(row.orderNo) },
    { label: "商品名称", value: formatCellText(row.productName) },
    { label: "规格", value: formatCellText(row.spec) },
    { label: "数量", value: formatCellText(row.quantity) },
    { label: "物流单号", value: formatCellText(row.logisticsNo) },
    { label: "物流公司", value: formatCellText(row.logisticsCompany) },
    { label: "收货人", value: formatCellText(row.receiver) },
    { label: "收货人电话", value: formatCellText(row.phone) },
    { label: "收货人地址", value: formatCellText(row.address) },
    { label: "运费", value: formatShippingFee(row.shippingFee) },
    { label: "备注", value: formatCellText(row.remark) },
    { label: "分单日期", value: formatCellText(row.issueDate) },
    ...(row.afterSales === true
      ? [
          {
            label: "售后状态",
            value: formatCellText(row.afterSalesStatusLabel ?? "需售后"),
          },
          { label: "售后原因", value: formatCellText(row.afterSalesRemark) },
          { label: "售后时间", value: formatCellText(row.afterSalesAt) },
        ]
      : []),
  ];
}

/** 单行省略，悬停显示完整内容 */
function renderEllipsisCell(value: unknown) {
  const text = formatCellText(value);
  const isEmpty = text === "—";
  return (
    <Typography.Text
      className={isEmpty ? ORDER_TABLE_EMPTY_HINT_CLASS : undefined}
      type={isEmpty ? "secondary" : undefined}
      ellipsis={{ tooltip: text }}
      style={{ maxWidth: "100%" }}
    >
      {text}
    </Typography.Text>
  );
}

function parseRowKey(key: Key): string | null {
  if (key == null) {
    return null;
  }
  const text = String(key).trim();
  return text || null;
}

interface EditableMerchantCellProps {
  value: string;
  orderSystemNo?: string;
  orderDate: string;
  onUpdated: (context: OrderCellUpdatedContext) => void | Promise<void>;
}

interface EditableMerchantDisplayProps {
  value: string;
  orderSystemNo?: string;
  onStartEdit: () => void;
}

/** 商家列展示态：无 draft/saving 等编辑 hooks */
const EditableMerchantDisplay = memo(function EditableMerchantDisplay({
  value,
  orderSystemNo,
  onStartEdit,
}: EditableMerchantDisplayProps) {
  const displayText = value.trim() || "点击设置";
  const isPending = value === PENDING_SPLIT_MERCHANT;

  return (
    <Typography.Text
      className={
        isPending || !value.trim() ? ORDER_TABLE_EMPTY_HINT_CLASS : undefined
      }
      type={isPending || !value.trim() ? "secondary" : undefined}
      style={{
        cursor: !orderSystemNo ? "not-allowed" : "pointer",
        userSelect: "none",
      }}
      ellipsis={{ tooltip: value || "点击设置商家" }}
      onClick={() => {
        if (orderSystemNo) {
          onStartEdit();
        }
      }}
    >
      {displayText}
    </Typography.Text>
  );
});

interface EditableMerchantEditorProps extends EditableMerchantCellProps {
  onClose: () => void;
}

/** 商家列编辑态：仅在点击后挂载 */
function EditableMerchantEditor({
  value,
  orderSystemNo,
  orderDate,
  onUpdated,
  onClose,
}: EditableMerchantEditorProps) {
  const [draft, setDraft] = useState(value);
  const [saving, setSaving] = useState(false);
  const inputRef = useRef<InputRef>(null);

  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const cancelEdit = () => {
    setDraft(value);
    onClose();
  };

  const commit = async () => {
    const trimmed = draft.trim();
    if (!orderSystemNo) {
      cancelEdit();
      return;
    }
    if (trimmed === "" || trimmed === value.trim()) {
      cancelEdit();
      return;
    }
    setSaving(true);
    try {
      await updateImportedOrderMerchant(orderSystemNo, trimmed, orderDate);
      await onUpdated({ patch: { merchant: trimmed } });
      onClose();
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "更新商家失败");
      setDraft(value);
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Input
      ref={inputRef}
      size="small"
      value={draft}
      maxLength={128}
      placeholder="输入商家"
      disabled={saving || !orderSystemNo}
      onChange={(event) => setDraft(event.target.value)}
      onBlur={() => void commit()}
      onPressEnter={() => void commit()}
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          cancelEdit();
        }
      }}
    />
  );
}

/** 表格内可编辑商家：点击后编辑，仅更新订单归属，不写入商家配置 */
const EditableMerchantCell = memo(function EditableMerchantCell({
  value,
  orderSystemNo,
  orderDate,
  onUpdated,
}: EditableMerchantCellProps) {
  const [editing, setEditing] = useState(false);
  const startEdit = useCallback(() => setEditing(true), []);

  if (editing) {
    return (
      <EditableMerchantEditor
        value={value}
        orderSystemNo={orderSystemNo}
        orderDate={orderDate}
        onUpdated={onUpdated}
        onClose={() => setEditing(false)}
      />
    );
  }

  return (
    <EditableMerchantDisplay
      value={value}
      orderSystemNo={orderSystemNo}
      onStartEdit={startEdit}
    />
  );
});

function normalizeShippingFeeValue(value: number | undefined): number {
  if (value == null || Number.isNaN(value)) {
    return 0;
  }
  return Math.round(value * 100) / 100;
}

function formatShippingFeeDraft(value: number | undefined): string {
  const normalized = normalizeShippingFeeValue(value);
  if (normalized === 0) {
    return "";
  }
  return String(normalized);
}

function parseShippingFeeDraft(text: string): number | null {
  const trimmed = text.trim();
  if (trimmed === "") {
    return 0;
  }
  const num = Number(trimmed);
  if (Number.isNaN(num)) {
    return null;
  }
  return normalizeShippingFeeValue(num);
}

interface EditableShippingFeeCellProps {
  value?: number;
  orderSystemNo?: string;
  orderDate: string;
  onUpdated: (context: OrderCellUpdatedContext) => void | Promise<void>;
}

interface EditableShippingFeeDisplayProps {
  normalizedValue: number;
  orderSystemNo?: string;
  onStartEdit: () => void;
}

const EditableShippingFeeDisplay = memo(function EditableShippingFeeDisplay({
  normalizedValue,
  orderSystemNo,
  onStartEdit,
}: EditableShippingFeeDisplayProps) {
  const displayText = formatShippingFee(normalizedValue);

  return (
    <Typography.Text
      style={{
        cursor: !orderSystemNo ? "not-allowed" : "pointer",
        userSelect: "none",
      }}
      ellipsis={{ tooltip: displayText }}
      onClick={() => {
        if (orderSystemNo) {
          onStartEdit();
        }
      }}
    >
      {displayText}
    </Typography.Text>
  );
});

interface EditableShippingFeeEditorProps extends EditableShippingFeeCellProps {
  onClose: () => void;
}

function EditableShippingFeeEditor({
  value,
  orderSystemNo,
  orderDate,
  onUpdated,
  onClose,
}: EditableShippingFeeEditorProps) {
  const normalizedValue = normalizeShippingFeeValue(value);
  const [draft, setDraft] = useState(formatShippingFeeDraft(normalizedValue));
  const [saving, setSaving] = useState(false);
  const inputRef = useRef<InputRef>(null);

  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const cancelEdit = () => {
    setDraft(formatShippingFeeDraft(normalizedValue));
    onClose();
  };

  const commit = async () => {
    if (!orderSystemNo) {
      cancelEdit();
      return;
    }
    const parsed = parseShippingFeeDraft(draft);
    if (parsed == null) {
      message.warning("请输入有效的运费金额");
      cancelEdit();
      return;
    }
    if (parsed === normalizedValue) {
      cancelEdit();
      return;
    }
    setSaving(true);
    try {
      await updateImportedOrderFields(
        orderSystemNo,
        { shippingFee: parsed },
        orderDate,
      );
      await onUpdated({ patch: { shippingFee: parsed } });
      onClose();
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "更新运费失败");
      setDraft(formatShippingFeeDraft(normalizedValue));
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Input
      ref={inputRef}
      size="small"
      value={draft}
      placeholder="输入运费"
      disabled={saving || !orderSystemNo}
      onChange={(event) => setDraft(event.target.value)}
      onBlur={() => void commit()}
      onPressEnter={() => void commit()}
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          cancelEdit();
        }
      }}
    />
  );
}

/** 表格内可编辑运费 */
const EditableShippingFeeCell = memo(function EditableShippingFeeCell({
  value,
  orderSystemNo,
  orderDate,
  onUpdated,
}: EditableShippingFeeCellProps) {
  const normalizedValue = normalizeShippingFeeValue(value);
  const [editing, setEditing] = useState(false);
  const startEdit = useCallback(() => setEditing(true), []);

  if (editing) {
    return (
      <EditableShippingFeeEditor
        value={value}
        orderSystemNo={orderSystemNo}
        orderDate={orderDate}
        onUpdated={onUpdated}
        onClose={() => setEditing(false)}
      />
    );
  }

  return (
    <EditableShippingFeeDisplay
      normalizedValue={normalizedValue}
      orderSystemNo={orderSystemNo}
      onStartEdit={startEdit}
    />
  );
});

type ProductPriceFieldKey = "costPrice" | "supplyPrice";

const SUPPLY_PRICE_SYNC_HINT_STORAGE_KEY = "supply_price_sync_hint_shown";

const PRODUCT_PRICE_FIELD_META: Record<
  ProductPriceFieldKey,
  { label: string; placeholder: string; syncHint: string }
> = {
  costPrice: {
    label: "成本价",
    placeholder: "输入成本价",
    syncHint: "将同步到相同商品名称+规格的所有订单",
  },
  supplyPrice: {
    label: "供货价",
    placeholder: "输入供货价",
    syncHint: "将同步到相同商品名称+规格+平台的所有订单",
  },
};

function showProductPriceSaveMessage(
  fieldKey: ProductPriceFieldKey,
  label: string,
) {
  const meta = PRODUCT_PRICE_FIELD_META[fieldKey];
  if (fieldKey === "supplyPrice") {
    const hintShown = sessionStorage.getItem(
      SUPPLY_PRICE_SYNC_HINT_STORAGE_KEY,
    );
    if (!hintShown) {
      sessionStorage.setItem(SUPPLY_PRICE_SYNC_HINT_STORAGE_KEY, "1");
      message.success(`${label}已保存，${meta.syncHint}`);
      return;
    }
    message.success(`${label}已保存`);
    return;
  }
  message.success(`${label}已保存，${meta.syncHint}`);
}

interface EditableProductPriceCellProps {
  fieldKey: ProductPriceFieldKey;
  value: number | undefined;
  orderSystemNo?: string;
  orderDate: string;
  onUpdated: (context: OrderCellUpdatedContext) => void | Promise<void>;
}

interface EditableProductPriceDisplayProps {
  normalizedValue: number;
  orderSystemNo?: string;
  onStartEdit: () => void;
}

const EditableProductPriceDisplay = memo(function EditableProductPriceDisplay({
  normalizedValue,
  orderSystemNo,
  onStartEdit,
}: EditableProductPriceDisplayProps) {
  const displayText =
    normalizedValue === 0 ? "点击编辑" : formatProductPrice(normalizedValue);
  const isEmptyHint = normalizedValue === 0;

  return (
    <Typography.Text
      className={isEmptyHint ? ORDER_TABLE_EMPTY_HINT_CLASS : undefined}
      type={isEmptyHint ? "secondary" : undefined}
      style={{
        cursor: !orderSystemNo ? "not-allowed" : "pointer",
        userSelect: "none",
      }}
      ellipsis={{ tooltip: displayText }}
      onClick={() => {
        if (orderSystemNo) {
          onStartEdit();
        }
      }}
    >
      {displayText}
    </Typography.Text>
  );
});

interface EditableProductPriceEditorProps extends EditableProductPriceCellProps {
  onClose: () => void;
}

/** 表格内可编辑成本价/供货价编辑态，保存后按组合键同步到其他订单 */
function EditableProductPriceEditor({
  fieldKey,
  value,
  orderSystemNo,
  orderDate,
  onUpdated,
  onClose,
}: EditableProductPriceEditorProps) {
  const meta = PRODUCT_PRICE_FIELD_META[fieldKey];
  const normalizedValue = normalizeShippingFeeValue(value);
  const [draft, setDraft] = useState(formatShippingFeeDraft(normalizedValue));
  const [saving, setSaving] = useState(false);
  const inputRef = useRef<InputRef>(null);
  const committingRef = useRef(false);

  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const cancelEdit = () => {
    setDraft(formatShippingFeeDraft(normalizedValue));
    onClose();
  };

  const commit = async () => {
    if (committingRef.current || saving) {
      return;
    }
    if (!orderSystemNo) {
      cancelEdit();
      return;
    }
    const parsed = parseShippingFeeDraft(draft);
    if (parsed == null) {
      message.warning(`请输入有效的${meta.label}`);
      cancelEdit();
      return;
    }
    if (parsed === normalizedValue) {
      cancelEdit();
      return;
    }
    committingRef.current = true;
    setSaving(true);
    try {
      await updateImportedOrderFields(
        orderSystemNo,
        { [fieldKey]: parsed },
        orderDate,
      );
      await onUpdated({ productPrice: { fieldKey, price: parsed } });
      showProductPriceSaveMessage(fieldKey, meta.label);
      onClose();
    } catch (err: unknown) {
      message.error(
        err instanceof Error ? err.message : `更新${meta.label}失败`,
      );
      setDraft(formatShippingFeeDraft(normalizedValue));
      onClose();
    } finally {
      committingRef.current = false;
      setSaving(false);
    }
  };

  return (
    <Input
      ref={inputRef}
      size="small"
      value={draft}
      placeholder={meta.placeholder}
      disabled={saving || !orderSystemNo}
      onChange={(event) => setDraft(event.target.value)}
      onBlur={() => void commit()}
      onPressEnter={(event) => {
        event.preventDefault();
        void commit();
      }}
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          cancelEdit();
        }
      }}
    />
  );
}

const EditableProductPriceCell = memo(function EditableProductPriceCell({
  fieldKey,
  value,
  orderSystemNo,
  orderDate,
  onUpdated,
}: EditableProductPriceCellProps) {
  const normalizedValue = normalizeShippingFeeValue(value);
  const [editing, setEditing] = useState(false);
  const startEdit = useCallback(() => setEditing(true), []);

  if (editing) {
    return (
      <EditableProductPriceEditor
        fieldKey={fieldKey}
        value={value}
        orderSystemNo={orderSystemNo}
        orderDate={orderDate}
        onUpdated={onUpdated}
        onClose={() => setEditing(false)}
      />
    );
  }

  return (
    <EditableProductPriceDisplay
      normalizedValue={normalizedValue}
      orderSystemNo={orderSystemNo}
      onStartEdit={startEdit}
    />
  );
});

const EDITABLE_ORDER_FIELD_CONFIG: Record<
  EditableOrderFieldKey,
  { maxLength: number; placeholder: string; errorLabel: string }
> = {
  orderNo: {
    maxLength: 64,
    placeholder: "输入订单编号",
    errorLabel: "订单编号",
  },
  logisticsNo: {
    maxLength: 128,
    placeholder: "多个单号可用中文或英文逗号分隔",
    errorLabel: "物流单号",
  },
  logisticsCompany: {
    maxLength: 128,
    placeholder: "输入物流公司",
    errorLabel: "物流公司",
  },
  receiver: {
    maxLength: 64,
    placeholder: "输入收货人",
    errorLabel: "收货人",
  },
  phone: {
    maxLength: 32,
    placeholder: "输入收货人电话",
    errorLabel: "收货人电话",
  },
  address: {
    maxLength: 512,
    placeholder: "输入收货人地址",
    errorLabel: "收货人地址",
  },
  remark: {
    maxLength: 512,
    placeholder: "输入备注",
    errorLabel: "备注",
  },
};

interface EditableOrderFieldCellProps {
  fieldKey: EditableOrderFieldKey;
  value: string;
  orderSystemNo?: string;
  orderDate: string;
  currentRow?: SplitTableRow;
  onUpdated: (context: OrderCellUpdatedContext) => void | Promise<void>;
}

interface EditableOrderFieldDisplayProps {
  fieldKey: EditableOrderFieldKey;
  value: string;
  orderSystemNo?: string;
  onStartEdit: () => void;
}

const EditableOrderFieldDisplay = memo(function EditableOrderFieldDisplay({
  fieldKey,
  value,
  orderSystemNo,
  onStartEdit,
}: EditableOrderFieldDisplayProps) {
  const fieldConfig = EDITABLE_ORDER_FIELD_CONFIG[fieldKey];
  const displayText = value.trim() || "点击编辑";
  const isEmptyHint = !value.trim();

  return (
    <Typography.Text
      className={isEmptyHint ? ORDER_TABLE_EMPTY_HINT_CLASS : undefined}
      type={isEmptyHint ? "secondary" : undefined}
      style={{
        cursor: !orderSystemNo ? "not-allowed" : "pointer",
        userSelect: "none",
      }}
      ellipsis={{ tooltip: value || fieldConfig.placeholder }}
      onClick={() => {
        if (orderSystemNo) {
          onStartEdit();
        }
      }}
    >
      {displayText}
    </Typography.Text>
  );
});

interface EditableOrderFieldEditorProps extends EditableOrderFieldCellProps {
  onClose: () => void;
}

/** 表格内可编辑订单字段编辑态：点击后挂载并保存到后端 */
function EditableOrderFieldEditor({
  fieldKey,
  value,
  orderSystemNo,
  orderDate,
  currentRow,
  onUpdated,
  onClose,
}: EditableOrderFieldEditorProps) {
  const fieldConfig = EDITABLE_ORDER_FIELD_CONFIG[fieldKey];
  const [draft, setDraft] = useState(value);
  const [saving, setSaving] = useState(false);
  const inputRef = useRef<InputRef>(null);

  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const cancelEdit = () => {
    setDraft(value);
    onClose();
  };

  const commit = async () => {
    const trimmed = draft.trim();
    if (!orderSystemNo) {
      cancelEdit();
      return;
    }
    if (trimmed === value.trim()) {
      cancelEdit();
      return;
    }
    setSaving(true);
    try {
      await updateImportedOrderFields(
        orderSystemNo,
        { [fieldKey]: trimmed },
        orderDate,
      );
      await onUpdated({
        patch: buildEditableFieldPatch(fieldKey, trimmed, currentRow),
      });
      onClose();
    } catch (err: unknown) {
      message.error(
        err instanceof Error
          ? err.message
          : `更新${fieldConfig.errorLabel}失败`,
      );
      setDraft(value);
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Input
      ref={inputRef}
      size="small"
      value={draft}
      maxLength={fieldConfig.maxLength}
      placeholder={fieldConfig.placeholder}
      disabled={saving || !orderSystemNo}
      onChange={(event) => setDraft(event.target.value)}
      onBlur={() => void commit()}
      onPressEnter={() => void commit()}
      onKeyDown={(event) => {
        if (event.key === "Escape") {
          cancelEdit();
        }
      }}
    />
  );
}

const EditableOrderFieldCell = memo(function EditableOrderFieldCell({
  fieldKey,
  value,
  orderSystemNo,
  orderDate,
  currentRow,
  onUpdated,
}: EditableOrderFieldCellProps) {
  const [editing, setEditing] = useState(false);
  const startEdit = useCallback(() => setEditing(true), []);

  if (editing) {
    return (
      <EditableOrderFieldEditor
        fieldKey={fieldKey}
        value={value}
        orderSystemNo={orderSystemNo}
        orderDate={orderDate}
        currentRow={currentRow}
        onUpdated={onUpdated}
        onClose={() => setEditing(false)}
      />
    );
  }

  return (
    <EditableOrderFieldDisplay
      fieldKey={fieldKey}
      value={value}
      orderSystemNo={orderSystemNo}
      onStartEdit={startEdit}
    />
  );
});

/** 表头 + 分页占用高度（scroll.y 容器为 table-scroll-viewport） */

export default function UploadPanel({ onProcessed }: UploadPanelProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [pageLoading, setPageLoading] = useState(true);
  const [headerLoading, setHeaderLoading] = useState(false);
  const [errorAlert, setErrorAlert] = useState<string | null>(null);
  const [matchedPlatform, setMatchedPlatform] = useState<string | null>(null);
  const [orderDataset, setOrderDataset] = useState<SplitResult | null>(null);
  const [activeMerchant, setActiveMerchant] =
    useState<string>(ALL_MERCHANT_TAB_KEY);
  const [selectedSystemNos, setSelectedSystemNos] = useState<string[]>([]);
  const [hasPlatforms, setHasPlatforms] = useState(true);
  /** 左侧快捷日期（单日选中时与 queryDateRange 一致） */
  const [sidebarSelectedDate, setSidebarSelectedDate] = useState<string | null>(
    () => formatLocalDateKey(),
  );
  /** 当前查询的分单日期区间 */
  const [queryDateRange, setQueryDateRange] = useState<DateRangeKey>(() =>
    createTodayDateRange(),
  );
  const [dateSummaries, setDateSummaries] = useState<ImportedDateSummary[]>([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  /** null 表示全部平台 */
  const [filterPlatform, setFilterPlatform] = useState<string | null>(null);
  /** null 表示全部回单状态 */
  const [filterReceiptStatus, setFilterReceiptStatus] = useState<string | null>(
    null,
  );
  /** 关键字搜索（后端 LIKE：商家、平台、系统编号、物流单号、订单编号） */
  const [searchKeyword, setSearchKeyword] = useState("");
  const searchKeywordRef = useRef("");
  const queryDateRangeRef = useRef(queryDateRange);
  const searchReadyRef = useRef(false);
  const filterPlatformRef = useRef<string | null>(null);
  const filterReceiptStatusRef = useRef<string | null>(null);
  const activeMerchantRef = useRef(activeMerchant);
  const tablePageRef = useRef(1);
  const tablePageSizeRef = useRef(20);
  const [tablePage, setTablePage] = useState(1);
  const [tablePageSize, setTablePageSize] = useState(20);

  useEffect(() => {
    searchKeywordRef.current = searchKeyword;
  }, [searchKeyword]);

  useEffect(() => {
    queryDateRangeRef.current = queryDateRange;
  }, [queryDateRange]);

  useEffect(() => {
    filterPlatformRef.current = filterPlatform;
  }, [filterPlatform]);

  useEffect(() => {
    filterReceiptStatusRef.current = filterReceiptStatus;
  }, [filterReceiptStatus]);

  useEffect(() => {
    activeMerchantRef.current = activeMerchant;
  }, [activeMerchant]);
  const [receiptModalOpen, setReceiptModalOpen] = useState(false);
  const [platformExportModalOpen, setPlatformExportModalOpen] = useState(false);
  const [merchantSplitModalOpen, setMerchantSplitModalOpen] = useState(false);
  const [receiptContent, setReceiptContent] = useState("");
  const [receiptSubmitting, setReceiptSubmitting] = useState(false);
  const receiptTextAreaRef = useRef<TextAreaRef>(null);
  const [detailRow, setDetailRow] = useState<SplitTableRow | null>(null);
  const [afterSalesRow, setAfterSalesRow] = useState<SplitTableRow | null>(
    null,
  );
  const [afterSalesRemark, setAfterSalesRemark] = useState("");
  const [afterSalesSubmitting, setAfterSalesSubmitting] = useState(false);
  const [duplicateModalOpen, setDuplicateModalOpen] = useState(false);
  const [duplicatePreview, setDuplicatePreview] =
    useState<ImportDuplicatePreview | null>(null);
  const [includeDuplicateOrderNos, setIncludeDuplicateOrderNos] =
    useState(false);
  const [duplicateImportSubmitting, setDuplicateImportSubmitting] =
    useState(false);
  const [pendingImport, setPendingImport] = useState<{
    file: File;
    mapping: ColumnMappingItem[];
    matchedPlatform: string | null;
  } | null>(null);

  const splitResult = useMemo(() => {
    if (orderDataset == null) {
      return null;
    }
    return deriveSplitResultView(orderDataset, filterPlatform, activeMerchant);
  }, [orderDataset, filterPlatform, activeMerchant]);

  const hasRangeOrders = (orderDataset?.pageRows?.length ?? 0) > 0;

  const platformFilterItems = useMemo((): PlatformFilterItem[] => {
    if (!splitResult?.platformSummaries?.length) {
      return [];
    }
    return [...splitResult.platformSummaries]
      .sort((left, right) =>
        left.platform.localeCompare(right.platform, "zh-CN"),
      )
      .map((item) => ({
        platform: item.platform,
        rowCount: item.rowCount,
        receiptedCount: item.receiptedCount ?? 0,
      }));
  }, [splitResult]);

  const platformExportTargets = useMemo((): PlatformFilterItem[] => {
    if (filterPlatform == null) {
      return platformFilterItems;
    }
    const selected = platformFilterItems.find(
      (item) => item.platform === filterPlatform,
    );
    if (selected) {
      return [selected];
    }
    return [
      {
        platform: filterPlatform,
        rowCount: 0,
        receiptedCount: 0,
      },
    ];
  }, [filterPlatform, platformFilterItems]);

  const hasPlatformExportTargets = useMemo(() => {
    if (filterPlatform == null) {
      return platformFilterItems.length > 0;
    }
    const selected = platformFilterItems.find(
      (item) => item.platform === filterPlatform,
    );
    return (selected?.receiptedCount ?? 0) > 0;
  }, [filterPlatform, platformFilterItems]);

  const hasPlatformExportReceipts = useMemo(
    () => platformExportTargets.some((item) => (item.receiptedCount ?? 0) > 0),
    [platformExportTargets],
  );

  const platformExportRangeLabel =
    queryDateRange.start === queryDateRange.end
      ? queryDateRange.start
      : `${queryDateRange.start} ~ ${queryDateRange.end}`;

  const hasActiveSearchKeyword = searchKeyword.trim() !== "";

  const isSingleDayQuery = queryDateRange.start === queryDateRange.end;

  const isPendingMerchantSplit = useMemo(
    () => hasPendingMerchantSplit(orderDataset),
    [orderDataset],
  );

  const displayPageRows = useMemo(
    () => splitResult?.pageRows ?? [],
    [splitResult],
  );

  const deferredDisplayPageRows = useDeferredValue(displayPageRows);

  // B：仅 Tab 筛选切换且 deferred 仍持有上一屏数据时延后更新；初始加载直接用最新数据
  const isTableFilterPending =
    hasRangeOrders &&
    deferredDisplayPageRows.length > 0 &&
    displayPageRows !== deferredDisplayPageRows;

  const resolvedTableRows = isTableFilterPending
    ? deferredDisplayPageRows
    : displayPageRows;

  // C：手动分页切片，Table 只渲染当前页行数
  const pagedTableRows = useMemo(() => {
    const start = (tablePage - 1) * tablePageSize;
    return resolvedTableRows.slice(start, start + tablePageSize);
  }, [resolvedTableRows, tablePage, tablePageSize]);

  /** 商家 Tab：当前平台下有订单的商家（含未定义） */
  const merchantGroupsForTabs = useMemo(() => {
    if (!splitResult) {
      return [];
    }
    return buildMerchantGroupsForTabs(splitResult.merchantGroups);
  }, [splitResult]);

  const merchantSplitTargets = useMemo((): MerchantSplitTargetItem[] => {
    if (!merchantGroupsForTabs.length) {
      return [];
    }
    return merchantGroupsForTabs
      .filter((group) => (group.rowCount ?? 0) > 0)
      .map((group) => ({
        merchant: group.merchant,
        rowCount: group.rowCount ?? 0,
      }));
  }, [merchantGroupsForTabs]);

  const hasMerchantSplitTargets = merchantSplitTargets.some(
    (item) => item.rowCount > 0,
  );

  const merchantSplitExportHint = buildMerchantSplitExportHint(filterPlatform);

  useEffect(() => {
    const hasPendingTab = merchantGroupsForTabs.some(
      (group) =>
        group.merchant === PENDING_SPLIT_MERCHANT && group.rowCount > 0,
    );
    if (activeMerchant === PENDING_SPLIT_MERCHANT && !hasPendingTab) {
      setActiveMerchant(ALL_MERCHANT_TAB_KEY);
      activeMerchantRef.current = ALL_MERCHANT_TAB_KEY;
      return;
    }
    if (
      orderDataset &&
      !merchantHasRowsUnderPlatform(
        orderDataset.pageRows ?? [],
        filterPlatform,
        activeMerchant,
      )
    ) {
      setActiveMerchant(ALL_MERCHANT_TAB_KEY);
      activeMerchantRef.current = ALL_MERCHANT_TAB_KEY;
    }
  }, [merchantGroupsForTabs, activeMerchant, orderDataset, filterPlatform]);

  const clearSelectedRows = useCallback(() => {
    setSelectedSystemNos([]);
  }, []);

  const applySplitResult = useCallback(
    (result: SplitResult, preserveFilters = false) => {
      if (!preserveFilters) {
        setFilterPlatform(null);
        filterPlatformRef.current = null;
        setFilterReceiptStatus(null);
        filterReceiptStatusRef.current = null;
        setSearchKeyword("");
      }
      setOrderDataset(result);
      if (!preserveFilters) {
        const merchantGroups = summarizeMerchantGroups(result.pageRows ?? []);
        if (merchantGroups.length > 0) {
          const nextMerchant = pickDefaultMerchant(merchantGroups, "");
          setActiveMerchant(nextMerchant);
          activeMerchantRef.current = nextMerchant;
        } else {
          setActiveMerchant("");
          activeMerchantRef.current = "";
        }
      }
      const filteredRows =
        deriveSplitResultView(
          result,
          filterPlatformRef.current,
          activeMerchantRef.current,
        ).pageRows ?? [];
      const validSystemNos = new Set(
        filteredRows
          .map((row) => row.systemNo)
          .filter((systemNo): systemNo is string => Boolean(systemNo)),
      );
      setSelectedSystemNos((prev) =>
        prev.filter((systemNo) => validSystemNos.has(systemNo)),
      );
    },
    [],
  );

  const refreshDateSummaries = useCallback(async () => {
    const summaries = await fetchImportedDateSummaries();
    setDateSummaries(summaries);
    return summaries;
  }, []);

  const loadOrdersForRange = useCallback(
    async (
      range: DateRangeKey,
      options: {
        preserveFilters?: boolean;
        keyword?: string;
      } = {},
    ) => {
      const preserveFilters = options.preserveFilters ?? false;
      const keyword =
        options.keyword !== undefined
          ? options.keyword
          : searchKeywordRef.current;
      if (!preserveFilters) {
        setFilterPlatform(null);
        filterPlatformRef.current = null;
        setFilterReceiptStatus(null);
        filterReceiptStatusRef.current = null;
        tablePageRef.current = 1;
        setTablePage(1);
      }
      setOrdersLoading(true);
      setErrorAlert(null);
      try {
        const result = await fetchImportedOrdersByDateRange(
          range.start,
          range.end,
          keyword,
          {
            receiptStatus:
              (filterReceiptStatusRef.current as ReceiptStatus | null) ??
              undefined,
          },
        );
        applySplitResult(result, preserveFilters);
        setQueryDateRange(range);
        queryDateRangeRef.current = range;
        return result;
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : "加载订单失败";
        setErrorAlert(msg);
        setOrderDataset(null);
        return null;
      } finally {
        setOrdersLoading(false);
      }
    },
    [applySplitResult],
  );

  const handleSelectFilterPlatform = useCallback(
    (platform: string | null) => {
      filterPlatformRef.current = platform;
      const currentMerchant = activeMerchantRef.current;
      if (
        orderDataset &&
        !merchantHasRowsUnderPlatform(
          orderDataset.pageRows ?? [],
          platform,
          currentMerchant,
        )
      ) {
        activeMerchantRef.current = ALL_MERCHANT_TAB_KEY;
        setActiveMerchant(ALL_MERCHANT_TAB_KEY);
      }
      setSelectedSystemNos([]);
      tablePageRef.current = 1;
      setTablePage(1);
      startTransition(() => {
        setFilterPlatform(platform);
      });
    },
    [orderDataset],
  );

  const reloadCurrentRange = useCallback(
    async (preserveFilters = true) => {
      await loadOrdersForRange(queryDateRangeRef.current, {
        preserveFilters,
        keyword: searchKeywordRef.current,
      });
    },
    [loadOrdersForRange],
  );

  const handleOrderCellUpdated = useCallback(
    (
      systemNo: string,
      sourceRow: SplitTableRow,
      context: OrderCellUpdatedContext,
    ) => {
      setOrderDataset((prev) => {
        if (prev == null) {
          return prev;
        }
        if (context.productPrice) {
          const { fieldKey, price } = context.productPrice;
          return propagateProductPriceInDataset(
            prev,
            sourceRow,
            fieldKey,
            price,
          );
        }
        if (context.patch) {
          return patchSplitResultAfterSalesRow(prev, systemNo, context.patch);
        }
        return prev;
      });
    },
    [],
  );

  const handleSelectSidebarDate = useCallback(
    async (date: string) => {
      if (
        queryDateRange.start === date &&
        queryDateRange.end === date &&
        sidebarSelectedDate === date
      ) {
        return;
      }
      const range = { start: date, end: date };
      setSidebarSelectedDate(date);
      setSelectedSystemNos([]);
      await loadOrdersForRange(range);
    },
    [queryDateRange, sidebarSelectedDate, loadOrdersForRange],
  );

  const handleRangeFilterChange = useCallback(
    async (values: [Dayjs | null, Dayjs | null] | null) => {
      if (values == null || values[0] == null || values[1] == null) {
        return;
      }
      if (
        !isSelectableSplitDate(values[0]) ||
        !isSelectableSplitDate(values[1])
      ) {
        message.warning("分单日期不能晚于今天");
        return;
      }
      const start = values[0].format("YYYY-MM-DD");
      const end = values[1].format("YYYY-MM-DD");
      if (queryDateRange.start === start && queryDateRange.end === end) {
        return;
      }
      const range = { start, end };
      setSidebarSelectedDate(start === end ? start : null);
      setSelectedSystemNos([]);
      await loadOrdersForRange(range);
    },
    [queryDateRange, loadOrdersForRange],
  );

  const handleTextFilterChange = useCallback((value: string) => {
    setSearchKeyword(value);
    setSelectedSystemNos([]);
  }, []);

  useEffect(() => {
    if (!searchReadyRef.current) {
      return;
    }
    const timer = window.setTimeout(() => {
      tablePageRef.current = 1;
      setTablePage(1);
      void loadOrdersForRange(queryDateRangeRef.current, {
        preserveFilters: true,
        keyword: searchKeyword,
      });
    }, 400);
    return () => window.clearTimeout(timer);
  }, [searchKeyword, loadOrdersForRange]);

  useEffect(() => {
    const init = async () => {
      setPageLoading(true);
      try {
        const [summaries, platforms] = await Promise.all([
          fetchImportedDateSummaries(),
          fetchPlatformTemplates(),
        ]);
        setDateSummaries(summaries);
        setHasPlatforms(platforms.length > 0);
        const initialRange = createTodayDateRange();
        setSidebarSelectedDate(initialRange.start);
        setQueryDateRange(initialRange);
        queryDateRangeRef.current = initialRange;
        await loadOrdersForRange(initialRange);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : "加载数据失败";
        setErrorAlert(msg);
      } finally {
        setPageLoading(false);
        searchReadyRef.current = true;
      }
    };
    void init();
    // 仅首次挂载时初始化，避免搜索词变化导致重复 init 并清空输入框
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const performImport = async (
    uploadFile: File,
    mapping: ColumnMappingItem[],
    matchedPlatform: string | null,
    includeDuplicates: boolean,
  ) => {
    const imported = await importOrdersByPlatform(
      uploadFile,
      mapping,
      includeDuplicates,
    );
    const today = formatLocalDateKey();
    const todayRange = { start: today, end: today };
    setSidebarSelectedDate(today);
    setQueryDateRange(todayRange);
    queryDateRangeRef.current = todayRange;
    await refreshDateSummaries();
    applySplitResult(imported);
    if (matchedPlatform) {
      message.success(
        `已导入 ${imported.totalRows} 条并按商家分单（平台：${matchedPlatform}）`,
      );
    } else {
      message.success(`已导入 ${imported.totalRows} 条并按商家分单`);
    }
  };

  const loadHeaders = async (uploadFile: File) => {
    setHeaderLoading(true);
    setErrorAlert(null);
    try {
      const result = await readExcelHeaders(uploadFile);
      const resolvedPlatform = result.matchedPlatform ?? null;
      setMatchedPlatform(resolvedPlatform);
      if (result.suggestedMapping.length === 0) {
        setErrorAlert("未能匹配表头，请检查系统配置中的平台模板");
        return;
      }
      const preview = await previewImportDuplicates(
        uploadFile,
        result.suggestedMapping,
      );
      if (!preview.orderNoMapped || preview.duplicateRowCount === 0) {
        await performImport(
          uploadFile,
          result.suggestedMapping,
          resolvedPlatform,
          false,
        );
        return;
      }
      setPendingImport({
        file: uploadFile,
        mapping: result.suggestedMapping,
        matchedPlatform: resolvedPlatform,
      });
      setDuplicatePreview(preview);
      setIncludeDuplicateOrderNos(false);
      setDuplicateModalOpen(true);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "读取表头失败";
      setErrorAlert(msg);
      setMatchedPlatform(null);
      setFile(null);
    } finally {
      setHeaderLoading(false);
    }
  };

  const handleCancelDuplicateImport = () => {
    setDuplicateModalOpen(false);
    setDuplicatePreview(null);
    setPendingImport(null);
    setIncludeDuplicateOrderNos(false);
    setFile(null);
    setMatchedPlatform(null);
  };

  const handleConfirmDuplicateImport = async () => {
    if (!pendingImport) {
      return;
    }
    setDuplicateImportSubmitting(true);
    setErrorAlert(null);
    try {
      await performImport(
        pendingImport.file,
        pendingImport.mapping,
        pendingImport.matchedPlatform,
        includeDuplicateOrderNos,
      );
      setDuplicateModalOpen(false);
      setDuplicatePreview(null);
      setPendingImport(null);
      setIncludeDuplicateOrderNos(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "导入失败";
      setErrorAlert(msg);
    } finally {
      setDuplicateImportSubmitting(false);
    }
  };

  const handlePickFile = () => {
    if (!hasPlatforms) {
      message.warning("请先在「系统配置 → 表头映射」中配置平台模板");
      return;
    }
    fileInputRef.current?.click();
  };

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const picked = event.target.files?.[0];
    event.target.value = "";
    if (!picked) {
      return;
    }
    setFile(picked);
    setMatchedPlatform(null);
    loadHeaders(picked);
  };

  const runMerchantSplit = async (options?: {
    platforms?: string[];
    merchants?: string[];
  }) => {
    const range = queryDateRangeRef.current;
    setLoading(true);
    setErrorAlert(null);
    try {
      const result = await assignPendingMerchantsForRange(
        range.start,
        range.end,
        options,
      );
      const exportDate = result.exportDate;
      const processedSystemNos = result.processedSystemNos ?? [];
      let splitExportFolderOpened = false;
      if (
        result.exportMode === "BROWSER_DOWNLOAD" &&
        result.exportedFileCount > 0 &&
        exportDate &&
        processedSystemNos.length > 0
      ) {
        const blob = result.exportDownloadToken
          ? await downloadSplitByMerchantExport({
              downloadToken: result.exportDownloadToken,
            })
          : await downloadSplitByMerchantExport({
              exportDate,
              systemNos: processedSystemNos,
            });
        downloadBlob(blob, `分单导出_${exportDate}.zip`);
      } else if (
        result.exportMode === "SERVER_DIRECTORY" &&
        result.exportedFileCount > 0 &&
        exportDate
      ) {
        try {
          await openSplitExportDirectory(exportDate);
          splitExportFolderOpened = true;
        } catch (openErr: unknown) {
          const openMsg =
            openErr instanceof Error ? openErr.message : "无法打开导出文件夹";
          message.warning(openMsg);
        }
      }
      await refreshDateSummaries();
      await reloadCurrentRange(true);
      setFile(null);
      setMatchedPlatform(null);
      const exportHint =
        result.exportedFileCount > 0 && exportDate
          ? result.exportMode === "BROWSER_DOWNLOAD"
            ? `，已下载 ${result.exportedFileCount} 个 Excel（ZIP）`
            : splitExportFolderOpened
              ? `，已导出 ${result.exportedFileCount} 个 Excel 到 ${result.exportDirectory ?? "导出目录"}/${formatExportDateFolderPath(exportDate)}/分单，并已打开文件夹`
              : `，已导出 ${result.exportedFileCount} 个 Excel 到 ${result.exportDirectory ?? "导出目录"}/${formatExportDateFolderPath(exportDate)}/分单`
          : "";
      const unmatchedHint =
        result.skippedCount > 0
          ? `，其中 ${result.skippedCount} 条未匹配仍归入未定义并导出`
          : "";
      const merchantHint =
        options?.platforms && options.platforms.length > 0
          ? `（${options.platforms.join("、")} 平台）`
          : "";
      message.success(
        `分单完成：共处理 ${result.assignedCount} 条${merchantHint}${unmatchedHint}${exportHint}`,
      );
      onProcessed?.();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "分单失败";
      setErrorAlert(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleConfirmMerchantSplit = async () => {
    if (!hasMerchantSplitTargets) {
      message.warning("当前没有可导出的商家订单");
      return;
    }
    const platforms = filterPlatform == null ? undefined : [filterPlatform];
    setMerchantSplitModalOpen(false);
    await runMerchantSplit({ platforms });
  };

  const handleDeleteSelected = async () => {
    if (selectedSystemNos.length === 0) {
      message.warning("请先勾选要删除的订单");
      return;
    }
    const count = selectedSystemNos.length;
    const currentRange = queryDateRangeRef.current;
    const isSingleDay = currentRange.start === currentRange.end;
    const singleDate = isSingleDay ? currentRange.start : null;
    const rowsBySystemNo = new Map(
      (splitResult?.pageRows ?? [])
        .filter((row): row is SplitTableRow & { systemNo: string } =>
          Boolean(row.systemNo),
        )
        .map((row) => [row.systemNo, row]),
    );
    const systemNosByDate = new Map<string, string[]>();
    for (const systemNo of selectedSystemNos) {
      const row = rowsBySystemNo.get(systemNo);
      if (row != null) {
        const dateKey = resolveRowIssueDateKey(row, currentRange.end);
        const bucket = systemNosByDate.get(dateKey) ?? [];
        bucket.push(row.systemNo);
        systemNosByDate.set(dateKey, bucket);
        continue;
      }
      if (singleDate != null) {
        const bucket = systemNosByDate.get(singleDate) ?? [];
        bucket.push(systemNo);
        systemNosByDate.set(singleDate, bucket);
      }
    }
    if (systemNosByDate.size === 0) {
      message.warning("未找到可删除的订单，请刷新后重试");
      return;
    }
    setDeleting(true);
    try {
      for (const [dateKey, systemNos] of systemNosByDate) {
        await deleteSelectedImportedOrders(systemNos, dateKey);
      }
      const deletedSystemNos = new Set(selectedSystemNos);
      setOrderDataset((prev) => {
        if (prev == null) {
          return prev;
        }
        return removeOrderRowsFromDataset(prev, deletedSystemNos);
      });
      clearSelectedRows();
      message.success(`已删除 ${count} 条`);
      void refreshDateSummaries().catch(() => {
        // 左侧日期汇总刷新失败不影响主表已重新加载
      });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "删除失败";
      setErrorAlert(msg);
    } finally {
      setDeleting(false);
    }
  };

  const handleExportSelected = async () => {
    if (selectedSystemNos.length === 0) {
      message.warning("请先勾选要导出的订单");
      return;
    }
    setExporting(true);
    try {
      const blob = await exportSelectedOrders(selectedSystemNos);
      const today = new Date().toISOString().slice(0, 10);
      downloadBlob(blob, `系统数据导出_${today}.xlsx`);
      message.success(`已导出 ${selectedSystemNos.length} 条`);
      clearSelectedRows();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "导出失败";
      setErrorAlert(msg);
    } finally {
      setExporting(false);
    }
  };

  const runPlatformExport = async (platforms?: string[]) => {
    const range = queryDateRangeRef.current;
    setExporting(true);
    try {
      const result = await exportReceiptByMerchant(
        range.start,
        range.end,
        platforms,
      );
      const exportDate = result.exportDate;
      let receiptExportFolderOpened = false;
      if (
        result.exportMode === "BROWSER_DOWNLOAD" &&
        result.exportedFileCount > 0
      ) {
        const blob = result.exportDownloadToken
          ? await downloadReceiptByMerchantExport({
              downloadToken: result.exportDownloadToken,
            })
          : await downloadReceiptByMerchantExport({
              startDate: range.start,
              endDate: range.end,
              platforms,
            });
        const downloadName =
          range.start === range.end
            ? `平台模版导出_${range.start}.zip`
            : `平台模版导出_${range.start}_${range.end}.zip`;
        downloadBlob(blob, downloadName);
      } else if (
        result.exportMode === "SERVER_DIRECTORY" &&
        result.exportedFileCount > 0 &&
        exportDate
      ) {
        try {
          await openReceiptExportDirectory(exportDate);
          receiptExportFolderOpened = true;
        } catch (openErr: unknown) {
          const openMsg =
            openErr instanceof Error ? openErr.message : "无法打开导出文件夹";
          message.warning(openMsg);
        }
      }
      const folderHint =
        result.exportMode === "SERVER_DIRECTORY" &&
        receiptExportFolderOpened &&
        exportDate
          ? `，已打开 ${result.exportDirectory ?? "导出目录"}/${formatExportDateFolderPath(exportDate)}/回单`
          : "";
      const platformHint =
        platforms != null && platforms.length > 0
          ? platforms.join("、")
          : "全部平台";
      message.success(
        `已导出 ${result.exportedFileCount} 个平台模版文件（${platformHint}）${folderHint}`,
      );
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "导出失败";
      setErrorAlert(msg);
    } finally {
      setExporting(false);
    }
  };

  const handleConfirmPlatformExport = async () => {
    const platforms =
      filterPlatform == null
        ? undefined
        : filterPlatform
          ? [filterPlatform]
          : [];
    if (!hasPlatformExportTargets) {
      message.warning("当前日期区间内没有可导出的平台");
      return;
    }
    if (filterPlatform != null && !hasPlatformExportReceipts) {
      message.warning("当前选中的平台没有可导出的已回单订单，请先填写物流信息");
      return;
    }
    setPlatformExportModalOpen(false);
    await runPlatformExport(platforms);
  };

  const handleDeleteRow = useCallback(
    async (row: SplitTableRow) => {
      if (!row.systemNo) {
        return;
      }
      try {
        const currentRange = queryDateRangeRef.current;
        const dateKey = resolveRowIssueDateKey(row, currentRange.end);
        await deleteImportedOrder(row.systemNo, dateKey);
        setOrderDataset((prev) => {
          if (prev == null) {
            return prev;
          }
          return removeOrderRowsFromDataset(
            prev,
            new Set([row.systemNo as string]),
          );
        });
        setSelectedSystemNos((prev) =>
          prev.filter((systemNo) => systemNo !== row.systemNo),
        );
        message.success("已删除");
        void refreshDateSummaries().catch(() => {
          // 左侧日期汇总刷新失败不影响主表已重新加载
        });
      } catch (err: unknown) {
        message.error(err instanceof Error ? err.message : "删除失败");
      }
    },
    [refreshDateSummaries],
  );

  const openAfterSalesModal = useCallback((row: SplitTableRow) => {
    if (row.afterSalesStatus === "PENDING") {
      return;
    }
    setAfterSalesRow(row);
    setAfterSalesRemark("");
  }, []);

  const handleCancelAfterSales = useCallback(async (row: SplitTableRow) => {
    if (!row.systemNo) {
      return;
    }
    try {
      const currentRange = queryDateRangeRef.current;
      const dateKey = resolveRowIssueDateKey(row, currentRange.end);
      await cancelImportedOrderAfterSales(row.systemNo, dateKey);
      setOrderDataset((prev) => {
        if (prev == null) {
          return prev;
        }
        return patchSplitResultAfterSalesRow(prev, row.systemNo, {
          afterSales: false,
          afterSalesStatus: "NONE",
          afterSalesStatusLabel: "无需售后",
          afterSalesRemark: undefined,
          afterSalesAt: undefined,
        });
      });
      message.success("已取消售后");
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "取消售后失败");
    }
  }, []);

  const handleSubmitAfterSales = async () => {
    if (!afterSalesRow?.systemNo) {
      return;
    }
    const remark = afterSalesRemark.trim();
    if (!remark) {
      message.warning("请填写售后原因");
      return;
    }
    setAfterSalesSubmitting(true);
    try {
      const currentRange = queryDateRangeRef.current;
      const dateKey = resolveRowIssueDateKey(afterSalesRow, currentRange.end);
      await markImportedOrderAfterSales(
        afterSalesRow.systemNo,
        remark,
        dateKey,
      );
      const markedSystemNo = afterSalesRow.systemNo;
      setOrderDataset((prev) => {
        if (prev == null) {
          return prev;
        }
        return patchSplitResultAfterSalesRow(prev, markedSystemNo, {
          afterSales: true,
          afterSalesStatus: "PENDING",
          afterSalesStatusLabel: "需售后",
          afterSalesRemark: remark,
        });
      });
      setAfterSalesRow(null);
      setAfterSalesRemark("");
      message.success("已标记为需售后");
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "标记售后失败");
    } finally {
      setAfterSalesSubmitting(false);
    }
  };

  const handleSubmitReceipt = async () => {
    const range = queryDateRangeRef.current;
    if (!receiptContent.trim()) {
      message.warning("请输入回单数据");
      return;
    }
    setReceiptSubmitting(true);
    setErrorAlert(null);
    try {
      const result = await batchUpdateReceipt(
        receiptContent,
        range.start,
        range.end,
      );
      setOrderDataset((prev) => {
        if (prev == null) {
          return prev;
        }
        return mergeOrderRowsIntoDataset(prev, result.orders.pageRows ?? []);
      });
      void refreshDateSummaries().catch(() => {
        // 左侧日期汇总刷新失败不影响主表已本地更新
      });
      setReceiptModalOpen(false);
      setReceiptContent("");
      message.success(`回单成功，已更新 ${result.updatedCount} 条`);
      if (result.notFoundLineCount > 0) {
        message.warning(
          `有 ${result.notFoundLineCount} 行系统单号未匹配：${result.notFoundSystemNos.slice(0, 5).join("、")}${
            result.notFoundSystemNos.length > 5 ? " 等" : ""
          }`,
        );
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "回单失败";
      setErrorAlert(msg);
    } finally {
      setReceiptSubmitting(false);
    }
  };

  const duplicatePreviewColumns = useMemo((): ColumnsType<ImportDuplicateRow> => {
    return [
      {
        title: "Excel 行号",
        dataIndex: "sourceRowNum",
        width: 90,
      },
      {
        title: "订单编号",
        dataIndex: "orderNo",
        width: 140,
        ellipsis: true,
      },
      {
        title: "商品名称",
        dataIndex: "productName",
        width: 160,
        ellipsis: true,
      },
      {
        title: "规格",
        dataIndex: "spec",
        width: 120,
        ellipsis: true,
      },
      {
        title: "数量",
        dataIndex: "quantity",
        width: 72,
      },
      {
        title: "收货人",
        dataIndex: "receiver",
        width: 100,
        ellipsis: true,
      },
      {
        title: "重复原因",
        dataIndex: "duplicateReason",
        width: 120,
        render: (reason: ImportDuplicateRow["duplicateReason"]) =>
          reason === "FILE" ? "文件内重复" : "历史订单重复",
      },
    ];
  }, []);

  const buildTableColumns = useCallback(
    (options: { showPlatformColumn: boolean }): ColumnsType<SplitTableRow> => {
      const notifyOrderCellUpdated = (
        record: SplitTableRow,
        context: OrderCellUpdatedContext,
      ) => {
        if (!record.systemNo) {
          return;
        }
        handleOrderCellUpdated(record.systemNo, record, context);
      };

      const receiptStatusColumn = {
        title: "回单状态",
        dataIndex: "receiptStatusLabel",
        width: 72,
        fixed: "left" as const,
        onCell: () => ({
          className: ORDER_TABLE_RECEIPT_STATUS_CELL_CLASS,
        }),
        render: (_: string, record: SplitTableRow) => {
          const receipted = resolveRowReceiptStatus(record) === "RECEIPTED";
          return (
            <Tag color={receipted ? "success" : "warning"}>
              {record.receiptStatusLabel || (receipted ? "已回单" : "未回单")}
            </Tag>
          );
        },
      };

      const merchantColumn = {
        title: "商家",
        dataIndex: "merchant",
        width: 72,
        fixed: "left" as const,
        ellipsis: true,
        render: (merchant: string | undefined, record: SplitTableRow) => (
          <EditableMerchantCell
            value={resolveRowMerchantName(merchant)}
            orderSystemNo={record.systemNo}
            orderDate={resolveRowIssueDateKey(record, queryDateRange.end)}
            onUpdated={(context) => notifyOrderCellUpdated(record, context)}
          />
        ),
      };

      const platformColumn = {
        title: "平台",
        dataIndex: "platform",
        width: 72,
        ellipsis: true,
        render: (platform: string | undefined) => renderEllipsisCell(platform),
      };

      const columns: ColumnsType<SplitTableRow> = [
        receiptStatusColumn,
        merchantColumn,
      ];

      if (options.showPlatformColumn) {
        columns.push(platformColumn);
      }

      columns.push(
        {
          title: "系统编号",
          dataIndex: "systemNo",
          width: 100,
          ellipsis: true,
          render: (systemNo: string | undefined) =>
            renderEllipsisCell(systemNo),
        },
        {
          title: "物流公司",
          dataIndex: "logisticsCompany",
          width: 72,
          ellipsis: true,
          render: (value: string | undefined, record: SplitTableRow) => (
            <EditableOrderFieldCell
              fieldKey="logisticsCompany"
              value={value ?? ""}
              orderSystemNo={record.systemNo}
              orderDate={resolveRowIssueDateKey(record, queryDateRange.end)}
              currentRow={record}
              onUpdated={(context) => notifyOrderCellUpdated(record, context)}
            />
          ),
        },
        {
          title: "物流单号",
          dataIndex: "logisticsNo",
          width: 130,
          ellipsis: true,
          render: (value: string | undefined, record: SplitTableRow) => (
            <EditableOrderFieldCell
              fieldKey="logisticsNo"
              value={value ?? ""}
              orderSystemNo={record.systemNo}
              orderDate={resolveRowIssueDateKey(record, queryDateRange.end)}
              currentRow={record}
              onUpdated={(context) => notifyOrderCellUpdated(record, context)}
            />
          ),
        },
        {
          title: "订单编号",
          dataIndex: "orderNo",
          width: 180,
          ellipsis: true,
          render: (value: string | undefined, record: SplitTableRow) => (
            <EditableOrderFieldCell
              fieldKey="orderNo"
              value={value ?? ""}
              orderSystemNo={record.systemNo}
              orderDate={resolveRowIssueDateKey(record, queryDateRange.end)}
              onUpdated={(context) => notifyOrderCellUpdated(record, context)}
            />
          ),
        },
        {
          title: "商品名称",
          dataIndex: "productName",
          width: 160,
          ellipsis: true,
          render: (value: string | undefined) => renderEllipsisCell(value),
        },
        {
          title: "规格",
          dataIndex: "spec",
          width: 72,
          ellipsis: true,
          render: (value: string | undefined) => renderEllipsisCell(value),
        },
        {
          title: "数量",
          dataIndex: "quantity",
          width: 72,
          align: "right" as const,
        },
        {
          title: "收货人",
          dataIndex: "receiver",
          width: 72,
          ellipsis: true,
          render: (value: string | undefined, record: SplitTableRow) => (
            <EditableOrderFieldCell
              fieldKey="receiver"
              value={value ?? ""}
              orderSystemNo={record.systemNo}
              orderDate={resolveRowIssueDateKey(record, queryDateRange.end)}
              onUpdated={(context) => notifyOrderCellUpdated(record, context)}
            />
          ),
        },
        {
          title: "收货人电话",
          dataIndex: "phone",
          width: 105,
          ellipsis: true,
          render: (value: string | undefined, record: SplitTableRow) => (
            <EditableOrderFieldCell
              fieldKey="phone"
              value={value ?? ""}
              orderSystemNo={record.systemNo}
              orderDate={resolveRowIssueDateKey(record, queryDateRange.end)}
              onUpdated={(context) => notifyOrderCellUpdated(record, context)}
            />
          ),
        },
        {
          title: "收货人地址",
          dataIndex: "address",
          width: 120,
          ellipsis: true,
          render: (value: string | undefined, record: SplitTableRow) => (
            <EditableOrderFieldCell
              fieldKey="address"
              value={value ?? ""}
              orderSystemNo={record.systemNo}
              orderDate={resolveRowIssueDateKey(record, queryDateRange.end)}
              onUpdated={(context) => notifyOrderCellUpdated(record, context)}
            />
          ),
        },
        {
          title: "运费",
          dataIndex: "shippingFee",
          width: 72,
          align: "right" as const,
          render: (value: number | undefined, record: SplitTableRow) => (
            <EditableShippingFeeCell
              value={value}
              orderSystemNo={record.systemNo}
              orderDate={resolveRowIssueDateKey(record, queryDateRange.end)}
              onUpdated={(context) => notifyOrderCellUpdated(record, context)}
            />
          ),
        },
        {
          title: "成本价",
          dataIndex: "costPrice",
          width: 72,
          align: "right" as const,
          render: (value: number | undefined, record: SplitTableRow) => (
            <EditableProductPriceCell
              fieldKey="costPrice"
              value={value}
              orderSystemNo={record.systemNo}
              orderDate={resolveRowIssueDateKey(record, queryDateRange.end)}
              onUpdated={(context) => notifyOrderCellUpdated(record, context)}
            />
          ),
        },
        {
          title: "供货价",
          dataIndex: "supplyPrice",
          width: 72,
          align: "right" as const,
          render: (value: number | undefined, record: SplitTableRow) => (
            <EditableProductPriceCell
              fieldKey="supplyPrice"
              value={value}
              orderSystemNo={record.systemNo}
              orderDate={resolveRowIssueDateKey(record, queryDateRange.end)}
              onUpdated={(context) => notifyOrderCellUpdated(record, context)}
            />
          ),
        },
        {
          title: "备注",
          dataIndex: "remark",
          width: 120,
          ellipsis: true,
          render: (value: string | undefined, record: SplitTableRow) => (
            <EditableOrderFieldCell
              fieldKey="remark"
              value={value ?? ""}
              orderSystemNo={record.systemNo}
              orderDate={resolveRowIssueDateKey(record, queryDateRange.end)}
              onUpdated={(context) => notifyOrderCellUpdated(record, context)}
            />
          ),
        },
        {
          title: "分单日期",
          dataIndex: "issueDate",
          width: 100,
          ellipsis: true,
          render: (value: string | undefined) =>
            renderEllipsisCell(formatIssueDateDisplay(value)),
        },
        {
          title: "操作",
          key: "action",
          width: 180,
          fixed: "right" as const,
          onCell: () => ({
            className: ORDER_TABLE_ACTION_CELL_CLASS,
          }),
          render: (_, record) => (
            <Space size={0} wrap>
              <Button
                type="link"
                size="small"
                onClick={() => setDetailRow(record)}
              >
                详情
              </Button>
              {record.afterSalesStatus === "PENDING" ? (
                <Popconfirm
                  title="确定取消该订单的售后标记？"
                  description="订单不会被删除，仅恢复为无需售后状态。"
                  onConfirm={() => void handleCancelAfterSales(record)}
                >
                  <Button
                    type="link"
                    size="small"
                    danger
                    disabled={!record.systemNo}
                  >
                    取消售后
                  </Button>
                </Popconfirm>
              ) : (
                <Button
                  type="link"
                  size="small"
                  disabled={!record.systemNo}
                  onClick={() => openAfterSalesModal(record)}
                >
                  售后
                </Button>
              )}
              <Popconfirm
                title="确定将该订单移入回收站？"
                description="移入后可在顶部菜单「回收站」中恢复或彻底删除。"
                onConfirm={() => handleDeleteRow(record)}
              >
                <Button
                  type="link"
                  danger
                  size="small"
                  disabled={!record.systemNo}
                >
                  删除
                </Button>
              </Popconfirm>
            </Space>
          ),
        },
      );

      return columns;
    },
    [
      handleCancelAfterSales,
      handleDeleteRow,
      handleOrderCellUpdated,
      openAfterSalesModal,
      queryDateRange,
    ],
  );

  const merchantTabKey =
    activeMerchant === ALL_MERCHANT_TAB_KEY || activeMerchant === ""
      ? ALL_MERCHANT_TAB_KEY
      : activeMerchant;

  const platformTabKey = filterPlatform ?? ALL_PLATFORM_TAB_KEY;

  const showPlatformColumn = filterPlatform == null;

  const orderTableColumns = useMemo(
    () => buildTableColumns({ showPlatformColumn }),
    [buildTableColumns, showPlatformColumn],
  );
  const {
    resizableColumns: orderResizableColumns,
    tableComponents: orderTableComponents,
    scrollX: orderTableScrollX,
  } = useResizableColumns(orderTableColumns);
  const tableScrollViewportRef = useRef<HTMLDivElement>(null);
  const getTableStickyContainer = useCallback(
    () => tableScrollViewportRef.current ?? document.body,
    [],
  );
  const tableScrollY = useTableBodyScrollY(tableScrollViewportRef, {
    enabled: hasRangeOrders,
  });

  const handleTablePageChange = useCallback(
    (page: number, pageSize: number) => {
      tablePageRef.current = page;
      tablePageSizeRef.current = pageSize;
      setTablePage(page);
      setTablePageSize(pageSize);
    },
    [],
  );

  const selectableSystemNos = useMemo(
    () =>
      resolvedTableRows
        .map((row) => row.systemNo)
        .filter((systemNo): systemNo is string => Boolean(systemNo)),
    [resolvedTableRows],
  );

  const handleRowSelectionChange = useCallback(
    (keys: Key[], _selectedRows: SplitTableRow[], info: { type: string }) => {
      if (info.type === "all") {
        // 表头全选/取消：keys 在 preserveSelectedRowKeys 下取消时仍可能非空，按是否已全选切换
        const allSelected =
          selectableSystemNos.length > 0 &&
          selectableSystemNos.every((systemNo) =>
            selectedSystemNos.includes(systemNo),
          );
        setSelectedSystemNos(allSelected ? [] : [...selectableSystemNos]);
        return;
      }
      const selectableSystemNoSet = new Set(selectableSystemNos);
      const newSystemNos = keys
        .map(parseRowKey)
        .filter(
          (systemNo): systemNo is string =>
            systemNo != null && selectableSystemNoSet.has(systemNo),
        );
      setSelectedSystemNos(newSystemNos);
    },
    [selectableSystemNos, selectedSystemNos],
  );

  const selectedRowKeys = useMemo(() => selectedSystemNos, [selectedSystemNos]);

  const handleMerchantTabChange = useCallback((merchantKey: string) => {
    activeMerchantRef.current = merchantKey;
    setSelectedSystemNos([]);
    tablePageRef.current = 1;
    setTablePage(1);
    startTransition(() => {
      setActiveMerchant(merchantKey);
    });
  }, []);

  const handleReceiptFilterChange = useCallback(
    (value: string) => {
      const status =
        value === ALL_RECEIPT_FILTER_KEY ? null : (value as ReceiptStatus);
      setFilterReceiptStatus(status);
      filterReceiptStatusRef.current = status;
      setSelectedSystemNos([]);
      tablePageRef.current = 1;
      setTablePage(1);
      void loadOrdersForRange(queryDateRangeRef.current, {
        preserveFilters: true,
      });
    },
    [loadOrdersForRange],
  );

  const tableRowSelection = useMemo(
    () => ({
      type: "checkbox" as const,
      columnWidth: 48,
      fixed: true as const,
      preserveSelectedRowKeys: true,
      selectedRowKeys,
      onChange: handleRowSelectionChange,
      getCheckboxProps: (record: SplitTableRow) => ({
        disabled: !record.systemNo,
      }),
    }),
    [selectedRowKeys, handleRowSelectionChange],
  );

  const tablePagination = useMemo(
    (): TablePaginationConfig => ({
      current: tablePage,
      pageSize: tablePageSize,
      total: resolvedTableRows.length,
      showSizeChanger: true,
      pageSizeOptions: ["10", "20", "50"],
      showTotal: (total: number) => `共 ${total} 条`,
      position: ["bottomRight"],
      onChange: (page: number, pageSize: number) =>
        handleTablePageChange(page, pageSize),
    }),
    [
      tablePage,
      tablePageSize,
      resolvedTableRows.length,
      handleTablePageChange,
    ],
  );

  const orderTableNode = useMemo(() => {
    if (!hasRangeOrders) {
      return null;
    }
    return (
      <div className="table-scroll-viewport" ref={tableScrollViewportRef}>
        <Table
          rowKey={(row) => rowKeyOf(row, row.merchant ?? "")}
          bordered
          size="small"
          tableLayout="fixed"
          loading={ordersLoading || isTableFilterPending}
          components={orderTableComponents}
          scroll={{
            x: orderTableScrollX + 48,
            y: tableScrollY,
          }}
          sticky={{
            offsetScroll: 0,
            getContainer: getTableStickyContainer,
          }}
          rowSelection={tableRowSelection}
          pagination={tablePagination}
          dataSource={pagedTableRows}
          columns={orderResizableColumns}
          rowClassName={(record) => resolveAfterSalesRowClassName(record)}
        />
      </div>
    );
  }, [
    hasRangeOrders,
    ordersLoading,
    orderTableComponents,
    orderTableScrollX,
    tableScrollY,
    getTableStickyContainer,
    tableRowSelection,
    tablePagination,
    isTableFilterPending,
    pagedTableRows,
    orderResizableColumns,
  ]);

  const merchantTabNavItems = useMemo(() => {
    if (!splitResult || !hasRangeOrders || merchantGroupsForTabs.length === 0) {
      return [];
    }
    const allMerchantsItem = {
      key: ALL_MERCHANT_TAB_KEY,
      label: "全部商家（已回单/总数）",
    };
    const merchantItems = merchantGroupsForTabs.map((group) => ({
      key: group.merchant,
      label: `${group.merchant}（${formatReceiptCountSummary(group.receiptedCount ?? 0, group.rowCount)}）`,
    }));
    return [allMerchantsItem, ...merchantItems];
  }, [splitResult, hasRangeOrders, merchantGroupsForTabs]);

  const platformTabNavItems = useMemo(() => {
    if (!splitResult || platformFilterItems.length === 0) {
      return [];
    }
    const allItem = {
      key: ALL_PLATFORM_TAB_KEY,
      label: "全部平台（已回单/总数）",
    };
    const platformItems = platformFilterItems.map((item) => ({
      key: item.platform,
      label: `${item.platform}（${formatReceiptCountSummary(item.receiptedCount, item.rowCount)}）`,
    }));
    return [allItem, ...platformItems];
  }, [splitResult, platformFilterItems]);

  const sidebarFinancialSummary = useMemo(
    () => summarizeSidebarFinancials(splitResult?.pageRows ?? []),
    [splitResult?.pageRows],
  );

  const toolbarSearchHint =
    splitResult && splitResult.totalRows > 0 && hasActiveSearchKeyword ? (
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        已按关键字在后端筛选结果
      </Typography.Text>
    ) : null;

  const sidebarSummaryCard =
    splitResult && hasRangeOrders ? (
      <div className="order-summary-card">
        <div className="order-summary-card-title">
          {isSingleDayQuery ? "当日汇总" : "区间汇总"}
        </div>
        <div className="order-summary-card-date">
          {formatSummaryDateLabel(queryDateRange, isSingleDayQuery)}
        </div>
        <div className="order-summary-row">
          <span>平台</span>
          <strong>{splitResult.platformCount} 个</strong>
        </div>
        <div className="order-summary-row">
          <span>商家</span>
          <strong>{countRealMerchants(splitResult.merchantGroups)} 个</strong>
        </div>
        <div className="order-summary-row">
          <span>订单</span>
          <strong>{splitResult.totalRows} 条</strong>
        </div>
        <div className="order-summary-row order-summary-row-money">
          <span>营业额</span>
          <strong>{formatMoneyAmount(sidebarFinancialSummary.revenue)}</strong>
        </div>
        <div className="order-summary-row order-summary-row-money">
          <span>成本价</span>
          <strong>{formatMoneyAmount(sidebarFinancialSummary.cost)}</strong>
        </div>
        <div className="order-summary-row order-summary-row-money">
          <span>利润</span>
          <strong>{formatMoneyAmount(sidebarFinancialSummary.profit)}</strong>
        </div>
      </div>
    ) : null;

  const showTableLoading =
    (pageLoading || loading || ordersLoading) && !hasRangeOrders;
  const showEmpty =
    !pageLoading && !loading && !ordersLoading && !hasRangeOrders;

  const formatSidebarDateLabel = (item: ImportedDateSummary) => {
    if (item.today) {
      return "今天";
    }
    const parts = item.date.split("-");
    if (parts.length === 3) {
      return `${parts[1]}-${parts[2]}`;
    }
    return item.date;
  };

  return (
    <div className="order-workspace">
      <input
        ref={fileInputRef}
        type="file"
        accept=".xlsx,.xls"
        style={{ display: "none" }}
        onChange={handleFileChange}
      />

      {errorAlert && (
        <Alert
          type="error"
          showIcon
          closable
          message={errorAlert}
          onClose={() => setErrorAlert(null)}
          style={{ marginBottom: 12 }}
        />
      )}

      {!hasPlatforms && !pageLoading && (
        <Alert
          type="warning"
          showIcon
          message="配置不完整"
          description="请先在「系统配置 → 表头映射」中新增平台并保存模板。"
          style={{ marginBottom: 12 }}
        />
      )}

      <div className="order-main-layout">
        <aside className="order-filter-sider">
          <div className="order-filter-section">
            <div className="order-filter-sider-title">分单日期</div>
            <List
              size="small"
              loading={pageLoading}
              dataSource={dateSummaries}
              locale={{ emptyText: "暂无日期" }}
              renderItem={(item) => (
                <List.Item
                  className={
                    isDateKeyInRange(item.date, queryDateRange)
                      ? "order-filter-item-active"
                      : "order-filter-item"
                  }
                  onClick={() => void handleSelectSidebarDate(item.date)}
                >
                  <div className="order-filter-item-inner">
                    <span>{formatSidebarDateLabel(item)}</span>
                    <Badge
                      count={item.rowCount}
                      showZero
                      overflowCount={9999}
                      color={item.rowCount > 0 ? "blue" : "#d1d5db"}
                    />
                  </div>
                </List.Item>
              )}
            />
            {sidebarSummaryCard}
          </div>
        </aside>

        <div className="order-main-content">
          <div className="toolbar">
            <Space direction="vertical" size="middle" style={{ width: "100%" }}>
              <Space wrap size="middle" align="center">
                <Button
                  type="primary"
                  size="middle"
                  icon={<UploadOutlined />}
                  onClick={handlePickFile}
                  loading={headerLoading}
                  disabled={!hasPlatforms}
                >
                  上传订单 Excel
                </Button>
                {file && (
                  <Tag color="blue" className="file-tag">
                    {file.name}
                  </Tag>
                )}
                {file && matchedPlatform && (
                  <Tag color="green">平台：{matchedPlatform}</Tag>
                )}
                <Typography.Text type="secondary">分单日期</Typography.Text>
                <RangePicker
                  size="middle"
                  value={[
                    dayjs(queryDateRange.start),
                    dayjs(queryDateRange.end),
                  ]}
                  onChange={(values) => void handleRangeFilterChange(values)}
                  allowClear={false}
                  format="YYYY-MM-DD"
                  disabledDate={(current) =>
                    current != null && !isSelectableSplitDate(current)
                  }
                />
                <Input
                  allowClear
                  size="middle"
                  prefix={<SearchOutlined />}
                  placeholder="搜索商家、平台、系统编号、物流单号、订单编号"
                  value={searchKeyword}
                  onChange={(event) =>
                    handleTextFilterChange(event.target.value)
                  }
                  style={{ width: 350 }}
                />
                <Typography.Text type="secondary">回单状态</Typography.Text>
                <Select
                  size="middle"
                  value={filterReceiptStatus ?? ALL_RECEIPT_FILTER_KEY}
                  onChange={handleReceiptFilterChange}
                  style={{ width: 120 }}
                  options={[
                    { value: ALL_RECEIPT_FILTER_KEY, label: "全部" },
                    { value: "PENDING", label: "未回单" },
                    { value: "RECEIPTED", label: "已回单" },
                  ]}
                />
              </Space>
              <Space wrap size="middle">
                <Button
                  size="middle"
                  icon={<ScissorOutlined />}
                  onClick={() => {
                    if (!hasPendingMerchantSplit) {
                      message.warning("所选日期区间内没有订单，请先上传 Excel");
                      return;
                    }
                    setMerchantSplitModalOpen(true);
                  }}
                  loading={loading}
                  disabled={headerLoading || !isPendingMerchantSplit}
                >
                  按商家分单
                </Button>
                {splitResult != null && (
                  <>
                    <Button
                      size="middle"
                      icon={<PlusOutlined />}
                      onClick={() => setReceiptModalOpen(true)}
                    >
                      填写物流信息
                    </Button>
                    <Button
                      size="middle"
                      icon={<DownloadOutlined />}
                      onClick={() => setPlatformExportModalOpen(true)}
                      loading={exporting}
                    >
                      回单导出
                    </Button>
                    <Button
                      size="middle"
                      icon={<DownloadOutlined />}
                      onClick={() => void handleExportSelected()}
                      loading={exporting}
                      disabled={selectedSystemNos.length === 0}
                    >
                      所选数据导出
                    </Button>
                    <Popconfirm
                      title={`确定将选中的 ${selectedSystemNos.length} 条订单移入回收站？`}
                      description="移入后可在顶部菜单「回收站」中恢复或彻底删除。"
                      onConfirm={() => void handleDeleteSelected()}
                      disabled={selectedSystemNos.length === 0}
                    >
                      <Button
                        size="middle"
                        danger
                        icon={<DeleteOutlined />}
                        loading={deleting}
                        disabled={selectedSystemNos.length === 0}
                      >
                        批量删除
                      </Button>
                    </Popconfirm>
                  </>
                )}
              </Space>
              {toolbarSearchHint}
            </Space>
          </div>

          <div className="table-panel">
            {showTableLoading && (
              <div className="table-loading">
                <Spin
                  size="large"
                  tip={
                    loading
                      ? "正在分单..."
                      : ordersLoading
                        ? "正在加载订单..."
                        : "正在加载..."
                  }
                />
              </div>
            )}

            {showEmpty && (
              <div className="table-empty">
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={
                    hasActiveSearchKeyword ||
                    filterPlatform != null ||
                    filterReceiptStatus != null
                      ? "当前筛选条件下暂无订单"
                      : splitResult
                        ? `${splitResult.issueDate} 暂无订单，可上传 Excel 分单`
                        : "上传 Excel 后将自动匹配平台模板并入库；未匹配商家关键字时归入「未定义」"
                  }
                />
              </div>
            )}

            {hasRangeOrders && (
              <div className="table-panel-body">
                {platformTabNavItems.length > 0 && (
                  <Tabs
                    className="platform-tabs filter-tabs-nav-only"
                    activeKey={platformTabKey}
                    onChange={(key) =>
                      handleSelectFilterPlatform(
                        key === ALL_PLATFORM_TAB_KEY ? null : key,
                      )
                    }
                    items={platformTabNavItems}
                  />
                )}
                {merchantTabNavItems.length > 0 && (
                  <Tabs
                    className="merchant-tabs nested-merchant-tabs filter-tabs-nav-only"
                    activeKey={merchantTabKey}
                    onChange={handleMerchantTabChange}
                    items={merchantTabNavItems}
                  />
                )}
                {orderTableNode}
              </div>
            )}
          </div>
        </div>
      </div>

      <Modal
        title="订单详情"
        className="order-detail-modal"
        open={detailRow != null}
        onCancel={() => setDetailRow(null)}
        footer={
          <Button type="primary" onClick={() => setDetailRow(null)}>
            关闭
          </Button>
        }
        width={650}
        destroyOnClose
      >
        {detailRow != null && (
          <Descriptions
            className="order-detail-descriptions"
            column={1}
            bordered
            styles={{
              label: { width: 120, padding: "5px 10px" },
              content: { padding: "5px 10px" },
            }}
          >
            {resolveOrderDetailItems(detailRow).map((item) => (
              <Descriptions.Item key={item.label} label={item.label}>
                <Typography.Text
                  style={{
                    wordBreak:
                      item.label === "收货人地址" ? "break-all" : undefined,
                  }}
                >
                  {item.value}
                </Typography.Text>
              </Descriptions.Item>
            ))}
          </Descriptions>
        )}
      </Modal>

      <Modal
        title="按商家分单"
        open={merchantSplitModalOpen}
        onCancel={() => setMerchantSplitModalOpen(false)}
        onOk={() => void handleConfirmMerchantSplit()}
        confirmLoading={loading}
        okText="确认分单"
        cancelText="取消"
        okButtonProps={{ disabled: !hasMerchantSplitTargets }}
        width={520}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
          日期区间：{platformExportRangeLabel}
        </Typography.Paragraph>
        <Typography.Paragraph style={{ marginBottom: 8 }}>
          {merchantSplitExportHint}
        </Typography.Paragraph>
        {merchantSplitTargets.length > 0 ? (
          <List
            size="small"
            bordered
            dataSource={merchantSplitTargets}
            renderItem={(item) => (
              <List.Item>
                <Space>
                  <span>{item.merchant}</span>
                  <Typography.Text type="secondary">
                    {item.rowCount} 条
                  </Typography.Text>
                </Space>
              </List.Item>
            )}
          />
        ) : (
          <Empty description="没有可导出的商家" />
        )}
        {!hasMerchantSplitTargets && merchantSplitTargets.length > 0 && (
          <Typography.Paragraph type="warning" style={{ marginTop: 12 }}>
            当前没有可导出的商家订单。
          </Typography.Paragraph>
        )}
      </Modal>

      <Modal
        title="回单导出"
        open={platformExportModalOpen}
        onCancel={() => setPlatformExportModalOpen(false)}
        onOk={() => void handleConfirmPlatformExport()}
        confirmLoading={exporting}
        okText="确认导出"
        cancelText="取消"
        okButtonProps={{
          disabled:
            !hasPlatformExportTargets ||
            (filterPlatform != null && !hasPlatformExportReceipts),
        }}
        width={520}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
          日期区间：{platformExportRangeLabel}
        </Typography.Paragraph>
        <Typography.Paragraph style={{ marginBottom: 8 }}>
          {filterPlatform == null
            ? "将导出以下全部平台的已回单数据："
            : "将导出当前选中平台的已回单数据："}
        </Typography.Paragraph>
        {platformExportTargets.length > 0 ? (
          <List
            size="small"
            bordered
            dataSource={platformExportTargets}
            renderItem={(item) => (
              <List.Item>
                <Space>
                  <span>{item.platform}</span>
                  <Typography.Text type="secondary">
                    已回单 {item.receiptedCount} 条
                  </Typography.Text>
                </Space>
              </List.Item>
            )}
          />
        ) : (
          <Empty description="没有可导出的平台" />
        )}
        {!hasPlatformExportReceipts && platformExportTargets.length > 0 && (
          <Typography.Paragraph type="warning" style={{ marginTop: 12 }}>
            当前选中的平台没有可导出的已回单订单，请先填写物流信息。
          </Typography.Paragraph>
        )}
      </Modal>

      <Modal
        title="批量填写物流信息"
        open={receiptModalOpen}
        onCancel={() => setReceiptModalOpen(false)}
        onOk={() => void handleSubmitReceipt()}
        confirmLoading={receiptSubmitting}
        okText="提交物流信息"
        cancelText="取消"
        width={640}
        destroyOnClose
        afterOpenChange={(open) => {
          if (!open) {
            return;
          }
          window.setTimeout(() => {
            receiptTextAreaRef.current?.focus();
          }, 0);
        }}
      >
        <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
          将在所选分单日期区间（{platformExportRangeLabel}）内按系统单号匹配订单并更新物流信息。
          每行录入一条物流信息，包含系统单号、物流单号、物流公司即可（顺序不限，系统自动识别）。同一系统单号可填写多个物流单号，中文或英文逗号均可分隔。
        </Typography.Paragraph>
        <Input.TextArea
          ref={receiptTextAreaRef}
          autoFocus
          value={receiptContent}
          onChange={(event) => setReceiptContent(event.target.value)}
          placeholder={RECEIPT_PLACEHOLDER}
          rows={12}
          style={{ fontFamily: "monospace" }}
        />
      </Modal>

      <Modal
        title="标记需售后"
        open={afterSalesRow != null}
        onCancel={() => {
          setAfterSalesRow(null);
          setAfterSalesRemark("");
        }}
        onOk={() => void handleSubmitAfterSales()}
        confirmLoading={afterSalesSubmitting}
        okText="确认标记"
        cancelText="取消"
        width={520}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
          请填写本次售后原因。提交后订单将标记为「需售后」，并出现在「售后」页面；已完结的订单也可再次发起售后。
        </Typography.Paragraph>
        <Input.TextArea
          value={afterSalesRemark}
          onChange={(event) => setAfterSalesRemark(event.target.value)}
          placeholder="例如：商品破损、发错货、客户拒收等"
          rows={5}
          maxLength={512}
          showCount
        />
      </Modal>

      <Modal
        title="发现重复订单编号"
        open={duplicateModalOpen}
        onCancel={handleCancelDuplicateImport}
        onOk={() => void handleConfirmDuplicateImport()}
        confirmLoading={duplicateImportSubmitting}
        okText="确认导入"
        cancelText="取消"
        width={920}
        destroyOnClose
      >
        {duplicatePreview != null && (
          <>
            <Typography.Paragraph style={{ marginBottom: 8 }}>
              共检测到 {duplicatePreview.duplicateOrderNos.length} 个重复订单编号，涉及{" "}
              {duplicatePreview.duplicateRowCount} 行数据（比对范围含历史订单与归档订单）。
            </Typography.Paragraph>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
              重复编号：
              {duplicatePreview.duplicateOrderNos.join("、")}
            </Typography.Paragraph>
            <Table
              size="small"
              bordered
              rowKey={(row) => `${row.sourceRowNum}-${row.orderNo}`}
              columns={duplicatePreviewColumns}
              dataSource={duplicatePreview.duplicateRows}
              pagination={{
                pageSize: 8,
                showSizeChanger: false,
                hideOnSinglePage: true,
              }}
              scroll={{ x: 820, y: 280 }}
            />
            <Checkbox
              checked={includeDuplicateOrderNos}
              onChange={(event) =>
                setIncludeDuplicateOrderNos(event.target.checked)
              }
              style={{ marginTop: 12 }}
            >
              导入重复订单（不勾选则仅导入不重复的数据）
            </Checkbox>
          </>
        )}
      </Modal>
    </div>
  );
}
