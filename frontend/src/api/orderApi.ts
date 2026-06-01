import axios, { AxiosError } from "axios";

export interface TaskItem {
  taskId: number;
  originalFileName: string;
  operationType: string;
  status: string;
  message: string;
  inputRowCount: number;
  merchantGroupCount: number;
  outputRowCount: number;
  createdAt: string;
}

export interface OrderFieldMeta {
  fieldKey: string;
  label: string;
  required: boolean;
  aliases?: string[];
}

export interface ExcelHeader {
  columnIndex: number;
  headerName: string;
}

export interface ColumnMappingItem {
  fieldKey: string;
  sourceIndex: number;
  enabled: boolean;
  sortOrder: number;
}

/** 未匹配 Excel 列时的占位索引 */
export const UNMAPPED_COLUMN_INDEX = -1;

/** 将映射补全为全部系统字段（含物流单号、物流公司） */
export function ensureCompleteMapping(
  mapping: ColumnMappingItem[],
  fields: OrderFieldMeta[],
  excelHeaders: ExcelHeader[],
): ColumnMappingItem[] {
  const usedColumns = new Set<number>();
  return fields.map((field, index) => {
    const existing = mapping.find((item) => item.fieldKey === field.fieldKey);
    if (existing) {
      const item = { ...existing, sortOrder: index };
      if (item.sourceIndex >= 0) {
        if (usedColumns.has(item.sourceIndex)) {
          return {
            ...item,
            sourceIndex: UNMAPPED_COLUMN_INDEX,
            enabled: false,
          };
        }
        usedColumns.add(item.sourceIndex);
      }
      return item;
    }
    const matched = excelHeaders.find(
      (header) =>
        !usedColumns.has(header.columnIndex) &&
        headerMatchesField(header.headerName, field),
    );
    if (matched) {
      usedColumns.add(matched.columnIndex);
    }
    return {
      fieldKey: field.fieldKey,
      sourceIndex: matched?.columnIndex ?? UNMAPPED_COLUMN_INDEX,
      enabled: matched != null,
      sortOrder: index,
    };
  });
}

function headerMatchesField(headerName: string, field: OrderFieldMeta): boolean {
  const header = headerName.trim();
  if (!header) {
    return false;
  }
  const label = field.label.trim();
  if (label && header === label) {
    return true;
  }
  for (const alias of field.aliases ?? []) {
    const token = alias.trim();
    if (token && header === token) {
      return true;
    }
  }
  return false;
}

export { headerMatchesField };

export interface ReadHeadersResult {
  headers: ExcelHeader[];
  suggestedMapping: ColumnMappingItem[];
  fields: OrderFieldMeta[];
  matchedPlatform?: string | null;
}

export type ReceiptStatus = "PENDING" | "RECEIPTED";

export type AfterSalesStatus = "NONE" | "PENDING" | "COMPLETED";

export interface SplitTableRow {
  /** 系统编号（10 位雪花 ID，主键） */
  systemNo: string;
  platform?: string;
  /** 分单商家（表格内可编辑） */
  merchant?: string;
  orderNo: string;
  productName: string;
  spec: string;
  quantity: number;
  receiver: string;
  address: string;
  phone: string;
  shippingFee: number;
  remark?: string;
  costPrice?: number;
  supplyPrice?: number;
  receiptStatus?: ReceiptStatus;
  receiptStatusLabel?: string;
  logisticsNo?: string;
  logisticsCompany?: string;
  issueDate: string;
  afterSales?: boolean;
  afterSalesRemark?: string;
  afterSalesAt?: string;
  afterSalesStatus?: AfterSalesStatus;
  afterSalesStatusLabel?: string;
}

export interface MerchantSplitGroup {
  merchant: string;
  rowCount: number;
  receiptedCount?: number;
  rows: SplitTableRow[];
}

export interface PlatformSummary {
  platform: string;
  rowCount: number;
  receiptedCount?: number;
}

export interface ImportedOrdersQuery {
  keyword?: string;
  platform?: string;
  merchant?: string;
  receiptStatus?: ReceiptStatus;
  afterSales?: boolean;
  afterSalesStatus?: AfterSalesStatus;
}

export interface UpdateImportedOrderFieldsPayload {
  orderNo?: string;
  logisticsNo?: string;
  logisticsCompany?: string;
  receiver?: string;
  phone?: string;
  address?: string;
  remark?: string;
  shippingFee?: number;
  costPrice?: number;
  supplyPrice?: number;
}

export type EditableOrderFieldKey = Exclude<
  keyof UpdateImportedOrderFieldsPayload,
  "shippingFee" | "costPrice" | "supplyPrice"
>;

export interface SplitResult {
  taskId?: number | null;
  issueDate: string;
  totalRows: number;
  platformCount: number;
  merchantCount: number;
  merchantGroups: MerchantSplitGroup[];
  platformSummaries?: PlatformSummary[];
  pageRows?: SplitTableRow[];
  /** 当前日期区间内可参与按商家分单的订单数 */
  splittableOrderCount?: number;
}

export const PENDING_SPLIT_MERCHANT = "未定义";

/** 所选日期区间内是否有订单可执行按商家分单 */
export function hasPendingMerchantSplit(result: SplitResult | null): boolean {
  if (!result) {
    return false;
  }
  if (typeof result.splittableOrderCount === "number") {
    return result.splittableOrderCount > 0;
  }
  if (result.totalRows === 0) {
    return false;
  }
  return result.merchantGroups.some(
    (group) =>
      group.merchant === PENDING_SPLIT_MERCHANT && (group.rowCount ?? 0) > 0,
  );
}

export interface BatchReceiptResult {
  updatedCount: number;
  parsedLineCount: number;
  notFoundLineCount: number;
  notFoundSystemNos: string[];
  orders: SplitResult;
}

export interface ImportedDateSummary {
  date: string;
  label: string;
  rowCount: number;
  today: boolean;
}

/** 本地日期 yyyy-MM-dd（与后端 Asia/Shanghai 发单日对齐时请保证服务器时区一致） */
export function formatLocalDateKey(date: Date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export interface PlatformTemplateSummary {
  platform: string;
  templateFileName?: string | null;
  updatedAt?: string;
}

export interface PlatformTemplateDetail {
  platform: string;
  templateFileName?: string | null;
  mapping: ColumnMappingItem[];
  templateHeaders: ExcelHeader[];
  updatedAt?: string;
}

export interface SavePlatformTemplatePayload {
  mapping: ColumnMappingItem[];
  templateHeaders: ExcelHeader[];
  templateFileName?: string;
}

export interface MerchantConfigItem {
  id: number;
  name: string;
  keywords: string[];
  updatedAt?: string;
}

export interface SaveMerchantConfigPayload {
  name: string;
  keywords: string[];
}

interface ApiErrorBody {
  success?: boolean;
  message?: string;
}

const client = axios.create({
  baseURL: "/api/orders",
  timeout: 120000,
});

export async function extractApiErrorMessage(error: unknown): Promise<string> {
  if (!axios.isAxiosError(error)) {
    return error instanceof Error ? error.message : "请求失败";
  }
  const axiosError = error as AxiosError;
  if (!axiosError.response) {
    return "无法连接后端，请确认服务已启动（端口 8080）";
  }
  const { data, status } = axiosError.response;
  if (data instanceof Blob) {
    try {
      const text = await data.text();
      const json = JSON.parse(text) as ApiErrorBody;
      if (json.message) {
        return json.message;
      }
    } catch {
      // 忽略
    }
  } else if (data && typeof data === "object" && "message" in data) {
    return String((data as ApiErrorBody).message);
  }
  return `请求失败（HTTP ${status}）`;
}

function appendMapping(
  formData: FormData,
  mapping: ColumnMappingItem[] | null,
) {
  if (mapping && mapping.length > 0) {
    const normalized = mapping.map((item, index) => ({
      ...item,
      sortOrder: index,
    }));
    formData.append("mapping", JSON.stringify(normalized));
  }
}

export async function fetchOrderFields(): Promise<OrderFieldMeta[]> {
  try {
    const { data } = await client.get<OrderFieldMeta[]>("/fields");
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export interface FieldAliasConfigItem {
  fieldKey: string;
  label: string;
  aliases: string[];
  updatedAt?: string;
}

export async function fetchFieldAliasConfigs(): Promise<FieldAliasConfigItem[]> {
  try {
    const { data } = await client.get<FieldAliasConfigItem[]>("/field-aliases");
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function saveFieldAliasConfig(
  fieldKey: string,
  aliases: string[],
): Promise<FieldAliasConfigItem> {
  try {
    const { data } = await client.put<FieldAliasConfigItem>(
      `/field-aliases/${encodeURIComponent(fieldKey)}`,
      { aliases },
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function fetchTodayImportedOrders(): Promise<SplitResult> {
  return fetchImportedOrdersByDate(formatLocalDateKey());
}

export async function fetchImportedDateSummaries(): Promise<
  ImportedDateSummary[]
> {
  try {
    const { data } = await client.get<ImportedDateSummary[]>("/imported/dates");
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function fetchImportedOrdersByDate(
  date: string,
): Promise<SplitResult> {
  try {
    const { data } = await client.get<SplitResult>("/imported", {
      params: { date },
    });
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function fetchImportedOrdersByDateRange(
  startDate: string,
  endDate: string,
  keyword?: string,
  query: ImportedOrdersQuery = {},
): Promise<SplitResult> {
  try {
    const trimmedKeyword = keyword?.trim();
    const params: Record<string, string | number> = {
      startDate,
      endDate,
    };
    if (trimmedKeyword) {
      params.keyword = trimmedKeyword;
    }
    if (query.platform) {
      params.platform = query.platform;
    }
    if (query.merchant) {
      params.merchant = query.merchant;
    }
    if (query.receiptStatus) {
      params.receiptStatus = query.receiptStatus;
    }
    if (query.afterSales != null) {
      params.afterSales = query.afterSales ? "true" : "false";
    }
    if (query.afterSalesStatus) {
      params.afterSalesStatus = query.afterSalesStatus;
    }
    const { data } = await client.get<SplitResult>("/imported", { params });
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

function importedOrderPath(systemNo: string) {
  return `/imported/${encodeURIComponent(systemNo)}`;
}

export async function deleteImportedOrder(
  systemNo: string,
  date: string,
): Promise<SplitResult> {
  try {
    const { data } = await client.delete<SplitResult>(
      importedOrderPath(systemNo),
      {
        params: { date },
      },
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function updateImportedOrderMerchant(
  systemNo: string,
  merchant: string,
  date: string,
): Promise<SplitResult> {
  try {
    const { data } = await client.put<SplitResult>(
      `${importedOrderPath(systemNo)}/merchant`,
      { merchant },
      { params: { date } },
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function updateImportedOrderFields(
  systemNo: string,
  payload: UpdateImportedOrderFieldsPayload,
  date: string,
): Promise<SplitResult> {
  try {
    const { data } = await client.put<SplitResult>(
      `${importedOrderPath(systemNo)}/fields`,
      payload,
      { params: { date } },
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function markImportedOrderAfterSales(
  systemNo: string,
  remark: string,
  date: string,
): Promise<SplitResult> {
  try {
    const { data } = await client.put<SplitResult>(
      `${importedOrderPath(systemNo)}/after-sales`,
      { remark },
      { params: { date } },
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function cancelImportedOrderAfterSales(
  systemNo: string,
  date: string,
): Promise<void> {
  try {
    await client.delete(`${importedOrderPath(systemNo)}/after-sales`, {
      params: { date },
    });
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function completeImportedOrderAfterSales(
  systemNo: string,
  date: string,
): Promise<void> {
  try {
    await client.put(`${importedOrderPath(systemNo)}/after-sales/complete`, null, {
      params: { date },
    });
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function fetchAfterSalesOrders(
  startDate: string,
  endDate: string,
  keyword?: string,
  afterSalesStatus: AfterSalesStatus = "PENDING",
): Promise<SplitResult> {
  return fetchImportedOrdersByDateRange(startDate, endDate, keyword, {
    afterSalesStatus,
  });
}

export async function deleteSelectedImportedOrders(
  systemNos: string[],
  date: string,
): Promise<SplitResult> {
  try {
    const { data } = await client.post<SplitResult>(
      "/imported/delete-selected",
      { systemNos },
      { params: { date } },
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function batchUpdateReceipt(
  content: string,
  date: string,
): Promise<BatchReceiptResult> {
  try {
    const { data } = await client.post<BatchReceiptResult>(
      "/imported/receipt/batch",
      { content },
      { params: { date } },
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

function pathSegment(name: string) {
  return encodeURIComponent(name);
}

export async function fetchPlatformTemplates(): Promise<
  PlatformTemplateSummary[]
> {
  try {
    const { data } = await client.get<PlatformTemplateSummary[]>(
      "/platform-templates",
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function createPlatformTemplate(
  platform: string,
): Promise<PlatformTemplateDetail> {
  try {
    const { data } = await client.post<PlatformTemplateDetail>(
      `/platform-templates/${pathSegment(platform)}`,
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function fetchPlatformTemplate(
  platform: string,
): Promise<PlatformTemplateDetail> {
  try {
    const { data } = await client.get<PlatformTemplateDetail>(
      `/platform-templates/${pathSegment(platform)}`,
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function savePlatformTemplate(
  platform: string,
  payload: SavePlatformTemplatePayload,
): Promise<PlatformTemplateDetail> {
  try {
    const { data } = await client.put<PlatformTemplateDetail>(
      `/platform-templates/${pathSegment(platform)}`,
      payload,
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function deletePlatformTemplate(platform: string): Promise<void> {
  try {
    await client.delete(`/platform-templates/${pathSegment(platform)}`);
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function fetchMerchantConfigs(): Promise<MerchantConfigItem[]> {
  try {
    const { data } =
      await client.get<MerchantConfigItem[]>("/merchant-configs");
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function createMerchantConfig(
  payload: SaveMerchantConfigPayload,
): Promise<MerchantConfigItem> {
  try {
    const { data } = await client.post<MerchantConfigItem>(
      "/merchant-configs",
      payload,
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function updateMerchantConfig(
  id: number,
  payload: SaveMerchantConfigPayload,
): Promise<MerchantConfigItem> {
  try {
    const { data } = await client.put<MerchantConfigItem>(
      `/merchant-configs/${id}`,
      payload,
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function deleteMerchantConfig(id: number): Promise<void> {
  try {
    await client.delete(`/merchant-configs/${id}`);
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export interface ProductPriceItem {
  platform: string;
  productName: string;
  spec: string;
  costPrice?: number;
  supplyPrice?: number;
}

export interface SaveProductPricePayload {
  platform?: string;
  productName: string;
  spec?: string;
  costPrice?: number;
  supplyPrice?: number;
}

export async function fetchProductPrices(params?: {
  keyword?: string;
}): Promise<ProductPriceItem[]> {
  try {
    const { data } = await client.get<ProductPriceItem[]>("/product-prices", {
      params,
    });
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function saveProductPrice(
  payload: SaveProductPricePayload,
): Promise<ProductPriceItem> {
  try {
    const { data } = await client.put<ProductPriceItem>(
      "/product-prices",
      payload,
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function batchDeleteProductPrices(
  items: ProductPriceItem[],
): Promise<{ deletedCount: number }> {
  try {
    const { data } = await client.delete<{ deletedCount: number }>(
      "/product-prices/batch",
      { data: { items } },
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export interface ProductPriceImportResult {
  importedCount: number;
  skippedCount: number;
  errors?: string[];
}

export async function importProductPrices(file: File): Promise<ProductPriceImportResult> {
  const formData = new FormData();
  formData.append("file", file);
  try {
    const { data } = await client.post<ProductPriceImportResult>(
      "/product-prices/import",
      formData,
      { headers: { "Content-Type": "multipart/form-data" } },
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function downloadProductPriceImportTemplate(): Promise<Blob> {
  try {
    const response = await client.get("/product-prices/import-template", {
      responseType: "blob",
    });
    return response.data as Blob;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function suggestExcelHeaders(
  file: File,
): Promise<ReadHeadersResult> {
  const formData = new FormData();
  formData.append("file", file);
  try {
    const { data } = await client.post<ReadHeadersResult>(
      "/read-headers/suggest",
      formData,
      {
        headers: { "Content-Type": "multipart/form-data" },
      },
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function readExcelHeaders(file: File): Promise<ReadHeadersResult> {
  const formData = new FormData();
  formData.append("file", file);
  try {
    const { data } = await client.post<ReadHeadersResult>(
      "/read-headers",
      formData,
      {
        headers: { "Content-Type": "multipart/form-data" },
      },
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function importOrdersByPlatform(
  file: File,
  mapping: ColumnMappingItem[] | null,
): Promise<SplitResult> {
  const formData = new FormData();
  formData.append("file", file);
  appendMapping(formData, mapping);
  try {
    const { data } = await client.post<SplitResult>("/import", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export interface AssignMerchantResult {
  assignedCount: number;
  skippedCount: number;
  exportDate: string;
  processedSystemNos: string[];
  exportDownloadToken?: string | null;
  exportedFiles: string[];
  exportedFileCount: number;
  exportMode: ExportMode;
  orders: SplitResult;
}

export type ExportMode = "SERVER_DIRECTORY" | "BROWSER_DOWNLOAD";

export interface ExportSettings {
  mode: ExportMode;
  updatedAt?: string | null;
}

export async function fetchExportSettings(): Promise<ExportSettings> {
  try {
    const { data } = await client.get<ExportSettings>("/export-settings");
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function saveExportSettings(
  mode: ExportMode,
): Promise<ExportSettings> {
  try {
    const { data } = await client.put<ExportSettings>("/export-settings", {
      mode,
    });
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export interface ReceiptExportResult {
  exportedFileCount: number;
  exportedFiles: string[];
  exportMode: ExportMode;
  exportDownloadToken?: string | null;
  exportDate?: string | null;
}

export async function downloadSplitByMerchantExport(
  options:
    | { downloadToken: string }
    | { exportDate: string; systemNos: string[] },
): Promise<Blob> {
  try {
    const params =
      "downloadToken" in options
        ? { downloadToken: options.downloadToken }
        : {
            exportDate: options.exportDate,
            systemNos: options.systemNos,
          };
    const response = await client.get("/export/split-by-merchant", {
      params,
      responseType: "blob",
    });
    const contentType = String(response.headers["content-type"] ?? "");
    if (contentType.includes("application/json")) {
      const text = await (response.data as Blob).text();
      const json = JSON.parse(text) as ApiErrorBody;
      throw new Error(json.message ?? "导出失败");
    }
    return response.data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function exportReceiptByMerchant(
  startDate: string,
  endDate: string,
  platforms?: string[],
): Promise<ReceiptExportResult> {
  try {
    const { data } = await client.post<ReceiptExportResult>(
      "/export/receipt-by-merchant",
      null,
      {
        params: {
          startDate,
          endDate,
          ...(platforms && platforms.length > 0 ? { platforms } : {}),
        },
      },
    );
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function downloadReceiptByMerchantExport(
  options:
    | { downloadToken: string }
    | { startDate: string; endDate: string; platforms?: string[] },
): Promise<Blob> {
  try {
    const params =
      "downloadToken" in options
        ? { downloadToken: options.downloadToken }
        : {
            startDate: options.startDate,
            endDate: options.endDate,
            ...(options.platforms && options.platforms.length > 0
              ? { platforms: options.platforms }
              : {}),
          };
    const response = await client.get("/export/receipt-by-merchant", {
      params,
      responseType: "blob",
    });
    const contentType = String(response.headers["content-type"] ?? "");
    if (contentType.includes("application/json")) {
      const text = await (response.data as Blob).text();
      const json = JSON.parse(text) as ApiErrorBody;
      throw new Error(json.message ?? "导出失败");
    }
    return response.data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function openSplitExportDirectory(exportDate: string): Promise<void> {
  try {
    await client.post("/export/open-split-directory", null, {
      params: { exportDate },
    });
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function openReceiptExportDirectory(exportDate: string): Promise<void> {
  try {
    await client.post("/export/open-receipt-directory", null, {
      params: { exportDate },
    });
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

/** @deprecated 使用 openReceiptExportDirectory */
export async function openReceiptExportDirectories(
  startDate: string,
  _endDate: string,
): Promise<void> {
  await openReceiptExportDirectory(startDate);
}

export async function assignPendingMerchantsForRange(
  startDate: string,
  endDate: string,
  options?: {
    platforms?: string[];
    merchants?: string[];
  },
): Promise<AssignMerchantResult> {
  try {
    const { data } = await client.post<AssignMerchantResult>("/split", null, {
      params: {
        startDate,
        endDate,
        ...(options?.platforms && options.platforms.length > 0
          ? { platforms: options.platforms }
          : {}),
        ...(options?.merchants && options.merchants.length > 0
          ? { merchants: options.merchants }
          : {}),
      },
    });
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

/** @deprecated 使用 assignPendingMerchantsForRange */
export async function assignAllPendingMerchants(
  listViewDate?: string | null,
): Promise<AssignMerchantResult> {
  const date = listViewDate ?? formatLocalDateKey(new Date());
  return assignPendingMerchantsForRange(date, date);
}

/** @deprecated 使用 assignPendingMerchantsForRange */
export async function assignMerchantsForDate(
  date: string,
): Promise<AssignMerchantResult> {
  return assignPendingMerchantsForRange(date, date);
}

/** @deprecated 请使用 {@link #importOrdersByPlatform}，上传导入已统一走 /import */
export async function splitByMerchant(
  file: File,
  mapping: ColumnMappingItem[] | null,
): Promise<SplitResult> {
  const formData = new FormData();
  formData.append("file", file);
  appendMapping(formData, mapping);
  try {
    const { data } = await client.post<SplitResult>("/split", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    return data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export interface ReconcileExportPayload {
  startDate: string;
  endDate: string;
  merchant?: string;
  platform?: string;
}

export interface AfterSalesExportPayload {
  startDate: string;
  endDate: string;
  keyword?: string;
}

async function downloadExcelExport(path: string, body: unknown): Promise<Blob> {
  try {
    const response = await client.post(path, body, { responseType: "blob" });
    const contentType = String(response.headers["content-type"] ?? "");
    if (contentType.includes("application/json")) {
      const text = await (response.data as Blob).text();
      const json = JSON.parse(text) as ApiErrorBody;
      throw new Error(json.message ?? "导出失败");
    }
    return response.data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function exportMerchantReconcile(
  payload: ReconcileExportPayload,
): Promise<Blob> {
  return downloadExcelExport("/export/reconcile/merchant", payload);
}

export async function exportPlatformReconcile(
  payload: ReconcileExportPayload,
): Promise<Blob> {
  return downloadExcelExport("/export/reconcile/platform", payload);
}

export async function exportAfterSalesOrders(
  payload: AfterSalesExportPayload,
): Promise<Blob> {
  return downloadExcelExport("/export/after-sales", payload);
}

export async function exportSelectedOrders(systemNos: string[]): Promise<Blob> {
  try {
    const response = await client.post(
      "/export/selected",
      { systemNos },
      { responseType: "blob" },
    );
    const contentType = String(response.headers["content-type"] ?? "");
    if (contentType.includes("application/json")) {
      const text = await (response.data as Blob).text();
      const json = JSON.parse(text) as ApiErrorBody;
      throw new Error(json.message ?? "导出失败");
    }
    return response.data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function exportPlatformTemplateSelected(
  systemNos: string[],
): Promise<Blob> {
  try {
    const response = await client.post(
      "/export/platform-template/selected",
      { systemNos },
      { responseType: "blob" },
    );
    const contentType = String(response.headers["content-type"] ?? "");
    if (contentType.includes("application/json")) {
      const text = await (response.data as Blob).text();
      const json = JSON.parse(text) as ApiErrorBody;
      throw new Error(json.message ?? "导出失败");
    }
    return response.data;
  } catch (error) {
    throw new Error(await extractApiErrorMessage(error));
  }
}

export async function fetchTasks(): Promise<TaskItem[]> {
  const { data } = await client.get<TaskItem[]>("/tasks");
  return data;
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}
