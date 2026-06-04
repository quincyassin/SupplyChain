import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Button,
  Checkbox,
  Input,
  Popconfirm,
  Space,
  Spin,
  Table,
  Typography,
  message,
} from "antd";
import type { InputRef } from "antd/es/input";
import {
  DeleteOutlined,
  DownloadOutlined,
  SearchOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import type { ColumnsType, TableRowSelection } from "antd/es/table/interface";
import {
  ProductPriceItem,
  SaveProductPricePayload,
  batchDeleteProductPrices,
  downloadBlob,
  downloadProductPriceImportTemplate,
  fetchProductPrices,
  importProductPrices,
  saveProductPrice,
} from "../api/orderApi";
import { useTableBodyScrollY } from "../hooks/useTableBodyScrollY";

const EMPTY_PRICE_HINT_CLASS = "order-table-empty-hint";

type ProductPriceFieldKey = "costPrice" | "supplyPrice";

function normalizePriceValue(value: number | undefined): number | undefined {
  if (value == null || Number.isNaN(value)) {
    return undefined;
  }
  return Math.round(value * 100) / 100;
}

function formatPriceDraft(value: number | undefined): string {
  const normalized = normalizePriceValue(value);
  if (normalized == null) {
    return "";
  }
  return String(normalized);
}

function parsePriceDraft(text: string): number | null {
  const trimmed = text.trim();
  if (trimmed === "") {
    return null;
  }
  const num = Number(trimmed);
  if (Number.isNaN(num) || num < 0) {
    return null;
  }
  return Math.round(num * 100) / 100;
}

function formatPriceDisplay(value: number | undefined): string {
  const normalized = normalizePriceValue(value);
  if (normalized == null) {
    return "点击编辑";
  }
  return normalized.toFixed(2);
}

function buildRowKey(row: ProductPriceItem): string {
  return `${row.platform}::${row.productName}::${row.spec}`;
}

function isPriceMaintained(value: number | undefined): boolean {
  return normalizePriceValue(value) != null;
}

/** 成本价或供货价任一未维护 */
function isProductPriceUnmaintained(item: ProductPriceItem): boolean {
  return !isPriceMaintained(item.costPrice) || !isPriceMaintained(item.supplyPrice);
}

function toSavePayloadFromRecord(
  record: ProductPriceItem,
  patch: Partial<Pick<ProductPriceItem, ProductPriceFieldKey>>,
): SaveProductPricePayload {
  return {
    platform: record.platform?.trim() ?? "",
    productName: record.productName.trim(),
    spec: record.spec?.trim() ?? "",
    costPrice: patch.costPrice ?? record.costPrice,
    supplyPrice: patch.supplyPrice ?? record.supplyPrice,
  };
}

interface EditableProductPriceCellProps {
  fieldKey: ProductPriceFieldKey;
  record: ProductPriceItem;
  onUpdated: (
    record: ProductPriceItem,
    fieldKey: ProductPriceFieldKey,
    price: number,
  ) => void;
}

function EditableProductPriceCell({
  fieldKey,
  record,
  onUpdated,
}: EditableProductPriceCellProps) {
  const label = fieldKey === "costPrice" ? "成本价" : "供货价";
  const value = record[fieldKey];
  const normalizedValue = normalizePriceValue(value);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(formatPriceDraft(normalizedValue));
  const [saving, setSaving] = useState(false);
  const inputRef = useRef<InputRef>(null);
  const committingRef = useRef(false);

  useEffect(() => {
    if (!editing) {
      setDraft(formatPriceDraft(normalizedValue));
    }
  }, [normalizedValue, editing]);

  useEffect(() => {
    if (editing) {
      inputRef.current?.focus();
      inputRef.current?.select();
    }
  }, [editing]);

  const cancelEdit = () => {
    setDraft(formatPriceDraft(normalizedValue));
    setEditing(false);
  };

  const commit = async () => {
    if (committingRef.current || saving) {
      return;
    }
    const parsed = parsePriceDraft(draft);
    if (parsed == null) {
      message.warning(`请输入有效的${label}`);
      cancelEdit();
      return;
    }
    if (parsed === normalizedValue) {
      cancelEdit();
      return;
    }

    const payload = toSavePayloadFromRecord(record, { [fieldKey]: parsed });
    if (payload.costPrice == null && payload.supplyPrice == null) {
      message.warning("请至少填写成本价或供货价");
      cancelEdit();
      return;
    }

    committingRef.current = true;
    setSaving(true);
    try {
      await saveProductPrice(payload);
      onUpdated(record, fieldKey, parsed);
      message.success(`${label}已保存`);
      setEditing(false);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : `更新${label}失败`);
      cancelEdit();
    } finally {
      committingRef.current = false;
      setSaving(false);
    }
  };

  if (editing) {
    return (
      <Input
        ref={inputRef}
        size="small"
        value={draft}
        placeholder={`输入${label}`}
        disabled={saving}
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

  const displayText = formatPriceDisplay(normalizedValue);
  const isEmptyHint = normalizedValue == null;

  return (
    <Typography.Text
      className={isEmptyHint ? EMPTY_PRICE_HINT_CLASS : undefined}
      type={isEmptyHint ? "secondary" : undefined}
      style={{ cursor: saving ? "wait" : "pointer", userSelect: "none" }}
      ellipsis={{ tooltip: displayText }}
      onClick={() => {
        if (!saving) {
          setEditing(true);
        }
      }}
    >
      {displayText}
    </Typography.Text>
  );
}

export default function ProductPricePage() {
  const [searchKeyword, setSearchKeyword] = useState("");
  const [onlyUnmaintainedPrices, setOnlyUnmaintainedPrices] = useState(false);
  const searchKeywordRef = useRef("");
  const searchReadyRef = useRef(false);
  const [loading, setLoading] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [importing, setImporting] = useState(false);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);
  const [errorAlert, setErrorAlert] = useState<string | null>(null);
  const [items, setItems] = useState<ProductPriceItem[]>([]);
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);
  const [tablePage, setTablePage] = useState(1);
  const [tablePageSize, setTablePageSize] = useState(20);
  const importInputRef = useRef<HTMLInputElement>(null);
  const tableScrollViewportRef = useRef<HTMLDivElement>(null);
  const getTableStickyContainer = useCallback(
    () => tableScrollViewportRef.current ?? document.body,
    [],
  );
  const tableVisible = !(loading && items.length === 0);
  const tableScrollY = useTableBodyScrollY(tableScrollViewportRef, {
    enabled: tableVisible,
  });

  useEffect(() => {
    searchKeywordRef.current = searchKeyword;
  }, [searchKeyword]);

  const loadProductPrices = useCallback(async (keyword?: string) => {
    setLoading(true);
    setErrorAlert(null);
    try {
      const list = await fetchProductPrices({
        keyword: (keyword ?? searchKeywordRef.current).trim() || undefined,
      });
      setItems(list);
      setTablePage(1);
      setSelectedRowKeys([]);
      return list;
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "加载商品价格失败";
      setErrorAlert(msg);
      setItems([]);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const init = async () => {
      await loadProductPrices("");
      searchReadyRef.current = true;
    };
    void init();
  }, [loadProductPrices]);

  useEffect(() => {
    if (!searchReadyRef.current) {
      return;
    }
    const timer = window.setTimeout(() => {
      void loadProductPrices();
    }, 400);
    return () => window.clearTimeout(timer);
  }, [searchKeyword, loadProductPrices]);

  const handleInlinePriceUpdated = useCallback(
    (
      record: ProductPriceItem,
      fieldKey: ProductPriceFieldKey,
      price: number,
    ) => {
      const rowKey = buildRowKey(record);
      setItems((prev) =>
        prev.map((item) =>
          buildRowKey(item) === rowKey ? { ...item, [fieldKey]: price } : item,
        ),
      );
    },
    [],
  );

  const displayItems = useMemo(() => {
    if (!onlyUnmaintainedPrices) {
      return items;
    }
    return items.filter(isProductPriceUnmaintained);
  }, [items, onlyUnmaintainedPrices]);

  const handleOnlyUnmaintainedPricesChange = (checked: boolean) => {
    setOnlyUnmaintainedPrices(checked);
    setTablePage(1);
    setSelectedRowKeys([]);
  };

  const tableEmptyText = onlyUnmaintainedPrices
    ? "暂无未维护成本价或供货价的商品"
    : "暂无订单商品，请先导入分单订单";

  const handleImportClick = () => {
    importInputRef.current?.click();
  };

  const handleDownloadImportTemplate = async () => {
    setDownloadingTemplate(true);
    try {
      const blob = await downloadProductPriceImportTemplate();
      downloadBlob(blob, "商品价格导入模板.xlsx");
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "下载导入模板失败");
    } finally {
      setDownloadingTemplate(false);
    }
  };

  const handleImportFileChange = async (
    event: React.ChangeEvent<HTMLInputElement>,
  ) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) {
      return;
    }
    setImporting(true);
    setErrorAlert(null);
    try {
      const result = await importProductPrices(file);
      await loadProductPrices();
      if (result.skippedCount > 0) {
        message.warning(
          `成功导入 ${result.importedCount} 条，跳过 ${result.skippedCount} 条`,
        );
      } else {
        message.success(`成功导入 ${result.importedCount} 条商品价格`);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "导入失败";
      setErrorAlert(msg);
      message.error(msg);
    } finally {
      setImporting(false);
    }
  };

  const selectedItems = useMemo(
    () =>
      displayItems.filter((item) => selectedRowKeys.includes(buildRowKey(item))),
    [displayItems, selectedRowKeys],
  );

  const handleBatchDelete = async () => {
    if (selectedItems.length === 0) {
      message.warning("请先选择要删除的记录");
      return;
    }
    setDeleting(true);
    try {
      const result = await batchDeleteProductPrices(selectedItems);
      await loadProductPrices();
      message.success(`已删除 ${result.deletedCount} 条记录`);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "批量删除失败");
    } finally {
      setDeleting(false);
    }
  };

  const rowSelection = useMemo<TableRowSelection<ProductPriceItem>>(
    () => ({
      selectedRowKeys,
      onChange: (keys) => setSelectedRowKeys(keys.map(String)),
    }),
    [selectedRowKeys],
  );

  const columns = useMemo<ColumnsType<ProductPriceItem>>(
    () => [
      {
        title: "平台",
        dataIndex: "platform",
        width: 120,
        ellipsis: true,
        render: (value: string | undefined) => value?.trim() || "—",
      },
      {
        title: "商品名称",
        dataIndex: "productName",
        width: 220,
        ellipsis: true,
      },
      {
        title: "规格",
        dataIndex: "spec",
        width: 160,
        ellipsis: true,
        render: (value: string | undefined) => (value?.trim() ? value : "—"),
      },
      {
        title: "成本价",
        dataIndex: "costPrice",
        width: 120,
        align: "right",
        render: (_value: number | undefined, record) => (
          <EditableProductPriceCell
            fieldKey="costPrice"
            record={record}
            onUpdated={handleInlinePriceUpdated}
          />
        ),
      },
      {
        title: "供货价",
        dataIndex: "supplyPrice",
        width: 120,
        align: "right",
        render: (_value: number | undefined, record) => (
          <EditableProductPriceCell
            fieldKey="supplyPrice"
            record={record}
            onUpdated={handleInlinePriceUpdated}
          />
        ),
      },
    ],
    [handleInlinePriceUpdated],
  );

  return (
    <div className="after-sales-page">
      {errorAlert && (
        <Alert
          type="error"
          showIcon
          closable
          message={errorAlert}
          onClose={() => setErrorAlert(null)}
          style={{ marginBottom: 8 }}
        />
      )}

      <div className="toolbar">
        <Space wrap size="middle" align="center">
          <Input
            allowClear
            size="middle"
            prefix={<SearchOutlined />}
            placeholder="搜索商品名称或平台"
            value={searchKeyword}
            onChange={(event) => setSearchKeyword(event.target.value)}
            style={{ width: 280 }}
          />
          <Checkbox
            checked={onlyUnmaintainedPrices}
            onChange={(event) =>
              handleOnlyUnmaintainedPricesChange(event.target.checked)
            }
          >
            未维护价格
          </Checkbox>
          <Button
            icon={<DownloadOutlined />}
            loading={downloadingTemplate}
            onClick={() => void handleDownloadImportTemplate()}
          >
            下载导入模板
          </Button>
          <Button
            icon={<UploadOutlined />}
            loading={importing}
            onClick={handleImportClick}
          >
            批量导入
          </Button>
          <input
            ref={importInputRef}
            type="file"
            accept=".xlsx,.xls"
            style={{ display: "none" }}
            onChange={(event) => void handleImportFileChange(event)}
          />
          <Popconfirm
            title={`确定删除选中的 ${selectedRowKeys.length} 条记录？`}
            description="删除后仅移除价格配置，不会删除订单数据。"
            onConfirm={() => void handleBatchDelete()}
            disabled={selectedRowKeys.length === 0}
          >
            <Button
              danger
              icon={<DeleteOutlined />}
              loading={deleting}
              disabled={selectedRowKeys.length === 0}
            >
              批量删除
            </Button>
          </Popconfirm>
          {selectedRowKeys.length > 0 && (
            <Typography.Text type="secondary">
              已选 {selectedRowKeys.length} 条
            </Typography.Text>
          )}
        </Space>
      </div>

      <div className="table-panel">
        {loading && items.length === 0 ? (
          <div className="table-loading">
            <Spin size="large" tip="正在加载商品价格..." />
          </div>
        ) : (
          <div className="table-panel-body">
            <div className="table-scroll-viewport" ref={tableScrollViewportRef}>
              <Table
                rowKey={buildRowKey}
                bordered
                size="small"
                tableLayout="fixed"
                loading={loading}
                rowSelection={rowSelection}
                columns={columns}
                dataSource={displayItems}
                scroll={{ x: 860, y: tableScrollY }}
                sticky={{
                  offsetScroll: 0,
                  getContainer: getTableStickyContainer,
                }}
                locale={{ emptyText: tableEmptyText }}
                pagination={{
                  current: tablePage,
                  pageSize: tablePageSize,
                  total: displayItems.length,
                  showSizeChanger: true,
                  pageSizeOptions: ["10", "20", "50"],
                  showTotal: (total) => `共 ${total} 条`,
                  position: ["bottomRight"],
                  onChange: (page, pageSize) => {
                    setTablePage(page);
                    setTablePageSize(pageSize);
                  },
                }}
              />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
