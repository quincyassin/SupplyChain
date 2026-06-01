import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
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
  PlusOutlined,
  SearchOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import type { ColumnsType, TableRowSelection } from "antd/es/table/interface";
import type { FormInstance } from "antd/es/form";
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

interface ProductPriceFormValues {
  platform: string;
  productName: string;
  spec: string;
  costPrice?: number;
  supplyPrice?: number;
}

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

function toSavePayload(values: ProductPriceFormValues): SaveProductPricePayload {
  return {
    platform: values.platform?.trim() ?? "",
    productName: values.productName.trim(),
    spec: values.spec.trim(),
    costPrice: values.costPrice,
    supplyPrice: values.supplyPrice,
  };
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
  onUpdated: (record: ProductPriceItem, fieldKey: ProductPriceFieldKey, price: number) => void;
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
  const searchKeywordRef = useRef("");
  const searchReadyRef = useRef(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [importing, setImporting] = useState(false);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);
  const [errorAlert, setErrorAlert] = useState<string | null>(null);
  const [items, setItems] = useState<ProductPriceItem[]>([]);
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [tablePage, setTablePage] = useState(1);
  const [tablePageSize, setTablePageSize] = useState(20);
  const formRef = useRef<FormInstance<ProductPriceFormValues>>(null);
  const importInputRef = useRef<HTMLInputElement>(null);
  const tableScrollViewportRef = useRef<HTMLDivElement>(null);
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
    (record: ProductPriceItem, fieldKey: ProductPriceFieldKey, price: number) => {
      const rowKey = buildRowKey(record);
      setItems((prev) =>
        prev.map((item) =>
          buildRowKey(item) === rowKey ? { ...item, [fieldKey]: price } : item,
        ),
      );
    },
    [],
  );

  const closeModal = () => {
    setModalOpen(false);
  };

  const openCreate = () => {
    setModalOpen(true);
  };

  const handleModalOk = async () => {
    const form = formRef.current;
    if (!form) {
      return;
    }
    let values: ProductPriceFormValues;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    if (values.costPrice == null && values.supplyPrice == null) {
      message.warning("请至少填写成本价或供货价");
      return;
    }
    if (!values.productName?.trim()) {
      message.warning("请输入商品名称");
      return;
    }

    setSaving(true);
    try {
      await saveProductPrice(toSavePayload(values));
      await loadProductPrices();
      message.success("商品价格已保存");
      closeModal();
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

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
    () => items.filter((item) => selectedRowKeys.includes(buildRowKey(item))),
    [items, selectedRowKeys],
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

  const createInitialValues: ProductPriceFormValues = {
    platform: "",
    productName: "",
    spec: "",
    costPrice: undefined,
    supplyPrice: undefined,
  };

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
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增
          </Button>
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

      <div className="after-sales-table-panel">
        {loading && items.length === 0 ? (
          <div className="table-loading">
            <Spin size="large" tip="正在加载商品价格..." />
          </div>
        ) : (
          <div className="table-scroll-viewport" ref={tableScrollViewportRef}>
            <Table
              rowKey={buildRowKey}
              bordered
              size="small"
              loading={loading}
              rowSelection={rowSelection}
              columns={columns}
              dataSource={items}
              scroll={{ x: 860, y: tableScrollY }}
              locale={{ emptyText: "暂无商品价格配置" }}
              pagination={{
                current: tablePage,
                pageSize: tablePageSize,
                total: items.length,
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
        )}
      </div>

      <Modal
        title="新增商品价格"
        open={modalOpen}
        onOk={() => void handleModalOk()}
        confirmLoading={saving}
        onCancel={closeModal}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        {modalOpen && (
          <Form
            key="create"
            ref={formRef}
            layout="vertical"
            initialValues={createInitialValues}
          >
            <Form.Item name="platform" label="平台">
              <Input placeholder="平台（可留空）" />
            </Form.Item>
            <Form.Item
              name="productName"
              label="商品名称"
              rules={[{ required: true, message: "请输入商品名称" }]}
            >
              <Input placeholder="商品名称" />
            </Form.Item>
            <Form.Item name="spec" label="规格">
              <Input placeholder="规格（可留空）" />
            </Form.Item>
            <Form.Item name="costPrice" label="成本价">
              <InputNumber
                min={0}
                precision={2}
                style={{ width: "100%" }}
                placeholder="成本价"
              />
            </Form.Item>
            <Form.Item name="supplyPrice" label="供货价">
              <InputNumber
                min={0}
                precision={2}
                style={{ width: "100%" }}
                placeholder="供货价"
              />
            </Form.Item>
          </Form>
        )}
      </Modal>
    </div>
  );
}
