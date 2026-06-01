import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Button,
  DatePicker,
  Descriptions,
  Empty,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from "antd";
import dayjs, { Dayjs } from "dayjs";
import { SearchOutlined, DownloadOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import {
  AfterSalesStatus,
  cancelImportedOrderAfterSales,
  completeImportedOrderAfterSales,
  downloadBlob,
  exportAfterSalesOrders,
  fetchAfterSalesOrders,
  formatLocalDateKey,
  PENDING_SPLIT_MERCHANT,
  SplitResult,
  SplitTableRow,
} from "../api/orderApi";

const { RangePicker } = DatePicker;

/** 售后页默认查询最近 30 天 */
const DEFAULT_AFTER_SALES_RANGE_DAYS = 30;

const AFTER_SALES_STATUS_OPTIONS: { value: AfterSalesStatus; label: string }[] =
  [
    { value: "PENDING", label: "需售后" },
    { value: "COMPLETED", label: "售后完结" },
  ];

interface DateRangeKey {
  start: string;
  end: string;
}

function createDefaultDateRange(): DateRangeKey {
  const end = formatLocalDateKey();
  const startDate = new Date();
  startDate.setDate(startDate.getDate() - (DEFAULT_AFTER_SALES_RANGE_DAYS - 1));
  return {
    start: formatLocalDateKey(startDate),
    end,
  };
}

function formatCellText(value: unknown): string {
  if (value == null || value === "") {
    return "—";
  }
  return String(value);
}

function resolveRowIssueDateKey(row: SplitTableRow, fallback: string): string {
  const raw = row.issueDate?.trim();
  if (raw && raw.length >= 10) {
    return raw.slice(0, 10);
  }
  return fallback;
}

function resolveAfterSalesDetailItems(row: SplitTableRow) {
  return [
    { label: "系统编号", value: formatCellText(row.systemNo) },
    { label: "订单编号", value: formatCellText(row.orderNo) },
    { label: "物流单号", value: formatCellText(row.logisticsNo) },
    { label: "物流公司", value: formatCellText(row.logisticsCompany) },
    { label: "商品名称", value: formatCellText(row.productName) },
    { label: "规格", value: formatCellText(row.spec) },
    { label: "数量", value: formatCellText(row.quantity) },
    { label: "收货人", value: formatCellText(row.receiver) },
    { label: "收货人电话", value: formatCellText(row.phone) },
    { label: "收货人地址", value: formatCellText(row.address) },
    {
      label: "售后状态",
      value: formatCellText(row.afterSalesStatusLabel ?? row.afterSalesStatus),
    },
    { label: "售后原因", value: formatCellText(row.afterSalesRemark) },
    { label: "售后时间", value: formatCellText(row.afterSalesAt) },
    { label: "订单日期", value: formatCellText(row.issueDate) },
  ];
}

function renderEllipsisCell(value: unknown) {
  const text = formatCellText(value);
  return (
    <Typography.Text ellipsis={{ tooltip: text }} style={{ maxWidth: "100%" }}>
      {text}
    </Typography.Text>
  );
}

function resolveStatusTagColor(status?: AfterSalesStatus): string {
  if (status === "COMPLETED") {
    return "green";
  }
  return "orange";
}

export default function AfterSalesPage() {
  const [queryDateRange, setQueryDateRange] = useState<DateRangeKey>(() =>
    createDefaultDateRange(),
  );
  const queryDateRangeRef = useRef(queryDateRange);
  const [afterSalesStatusFilter, setAfterSalesStatusFilter] =
    useState<AfterSalesStatus>("PENDING");
  const afterSalesStatusFilterRef = useRef<AfterSalesStatus>("PENDING");
  const [searchKeyword, setSearchKeyword] = useState("");
  const searchKeywordRef = useRef("");
  const searchReadyRef = useRef(false);
  const [loading, setLoading] = useState(false);
  const [errorAlert, setErrorAlert] = useState<string | null>(null);
  const [splitResult, setSplitResult] = useState<SplitResult | null>(null);
  const [detailRow, setDetailRow] = useState<SplitTableRow | null>(null);
  const [cancellingSystemNo, setCancellingSystemNo] = useState<string | null>(
    null,
  );
  const [completingSystemNo, setCompletingSystemNo] = useState<string | null>(
    null,
  );
  const [tablePage, setTablePage] = useState(1);
  const [tablePageSize, setTablePageSize] = useState(20);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    queryDateRangeRef.current = queryDateRange;
  }, [queryDateRange]);

  useEffect(() => {
    afterSalesStatusFilterRef.current = afterSalesStatusFilter;
  }, [afterSalesStatusFilter]);

  useEffect(() => {
    searchKeywordRef.current = searchKeyword;
  }, [searchKeyword]);

  const loadAfterSalesOrders = useCallback(
    async (
      range: DateRangeKey,
      keyword?: string,
      status: AfterSalesStatus = afterSalesStatusFilterRef.current,
    ) => {
      setLoading(true);
      setErrorAlert(null);
      try {
        const result = await fetchAfterSalesOrders(
          range.start,
          range.end,
          keyword ?? searchKeywordRef.current,
          status,
        );
        setSplitResult(result);
        setTablePage(1);
        return result;
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : "加载售后订单失败";
        setErrorAlert(msg);
        setSplitResult(null);
        return null;
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    const init = async () => {
      const range = createDefaultDateRange();
      setQueryDateRange(range);
      queryDateRangeRef.current = range;
      await loadAfterSalesOrders(range, "", "PENDING");
      searchReadyRef.current = true;
    };
    void init();
  }, [loadAfterSalesOrders]);

  useEffect(() => {
    if (!searchReadyRef.current) {
      return;
    }
    const timer = window.setTimeout(() => {
      void loadAfterSalesOrders(
        queryDateRangeRef.current,
        searchKeyword,
        afterSalesStatusFilterRef.current,
      );
    }, 400);
    return () => window.clearTimeout(timer);
  }, [searchKeyword, loadAfterSalesOrders]);

  useEffect(() => {
    if (!searchReadyRef.current) {
      return;
    }
    void loadAfterSalesOrders(
      queryDateRangeRef.current,
      searchKeywordRef.current,
      afterSalesStatusFilter,
    );
  }, [afterSalesStatusFilter, loadAfterSalesOrders]);

  const handleRangeFilterChange = useCallback(
    async (values: [Dayjs | null, Dayjs | null] | null) => {
      if (values == null || values[0] == null || values[1] == null) {
        return;
      }
      const range: DateRangeKey = {
        start: values[0].format("YYYY-MM-DD"),
        end: values[1].format("YYYY-MM-DD"),
      };
      setQueryDateRange(range);
      queryDateRangeRef.current = range;
      await loadAfterSalesOrders(range);
    },
    [loadAfterSalesOrders],
  );

  const handleCancelAfterSales = useCallback(
    async (row: SplitTableRow) => {
      if (!row.systemNo) {
        return;
      }
      setCancellingSystemNo(row.systemNo);
      try {
        const dateKey = resolveRowIssueDateKey(
          row,
          queryDateRangeRef.current.end,
        );
        await cancelImportedOrderAfterSales(row.systemNo, dateKey);
        await loadAfterSalesOrders(queryDateRangeRef.current);
        message.success("已取消售后，订单已从列表移除");
      } catch (err: unknown) {
        message.error(err instanceof Error ? err.message : "取消售后失败");
      } finally {
        setCancellingSystemNo(null);
      }
    },
    [loadAfterSalesOrders],
  );

  const handleCompleteAfterSales = useCallback(
    async (row: SplitTableRow) => {
      if (!row.systemNo) {
        return;
      }
      setCompletingSystemNo(row.systemNo);
      try {
        const dateKey = resolveRowIssueDateKey(
          row,
          queryDateRangeRef.current.end,
        );
        await completeImportedOrderAfterSales(row.systemNo, dateKey);
        await loadAfterSalesOrders(queryDateRangeRef.current);
        message.success("已标记售后完结");
      } catch (err: unknown) {
        message.error(err instanceof Error ? err.message : "售后完结失败");
      } finally {
        setCompletingSystemNo(null);
      }
    },
    [loadAfterSalesOrders],
  );

  const handleExport = useCallback(async () => {
    const range = queryDateRangeRef.current;
    setExporting(true);
    setErrorAlert(null);
    try {
      const blob = await exportAfterSalesOrders({
        startDate: range.start,
        endDate: range.end,
        keyword: searchKeywordRef.current.trim() || undefined,
      });
      const rangeLabel =
        range.start === range.end
          ? range.start
          : `${range.start}_${range.end}`;
      downloadBlob(blob, `售后订单_${rangeLabel}.xlsx`);
      message.success("售后订单导出成功");
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "导出失败";
      setErrorAlert(msg);
      message.error(msg);
    } finally {
      setExporting(false);
    }
  }, []);

  const pageRows = useMemo(() => {
    if (!splitResult?.pageRows) {
      return [];
    }
    return splitResult.pageRows.map((row) => ({
      ...row,
      merchant: row.merchant?.trim() ? row.merchant : PENDING_SPLIT_MERCHANT,
    }));
  }, [splitResult]);

  const statusFilterLabel = useMemo(
    () =>
      AFTER_SALES_STATUS_OPTIONS.find(
        (item) => item.value === afterSalesStatusFilter,
      )?.label ?? "需售后",
    [afterSalesStatusFilter],
  );

  const columns = useMemo<ColumnsType<SplitTableRow>>(
    () => [
      {
        title: "系统编号",
        dataIndex: "systemNo",
        width: 140,
        fixed: "left",
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "平台",
        dataIndex: "platform",
        width: 100,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "商家",
        dataIndex: "merchant",
        width: 100,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "订单编号",
        dataIndex: "orderNo",
        width: 120,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "商品名称",
        dataIndex: "productName",
        width: 160,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "收货人",
        dataIndex: "receiver",
        width: 90,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "收货人电话",
        dataIndex: "phone",
        width: 120,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "售后状态",
        dataIndex: "afterSalesStatusLabel",
        width: 100,
        render: (_: unknown, record) => (
          <Tag color={resolveStatusTagColor(record.afterSalesStatus)}>
            {formatCellText(record.afterSalesStatusLabel)}
          </Tag>
        ),
      },
      {
        title: "售后原因",
        dataIndex: "afterSalesRemark",
        width: 200,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "售后时间",
        dataIndex: "afterSalesAt",
        width: 170,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "订单日期",
        dataIndex: "issueDate",
        width: 170,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "操作",
        key: "action",
        width: 200,
        fixed: "right",
        render: (_, record) => {
          const isPending = record.afterSalesStatus === "PENDING";
          return (
            <Space size={0} wrap>
              <Button
                type="link"
                size="small"
                onClick={() => setDetailRow(record)}
              >
                详情
              </Button>
              {isPending && (
                <>
                  <Popconfirm
                    title="确定取消该订单的售后标记？"
                    description="取消后订单将从售后列表移除，恢复为无需售后。"
                    onConfirm={() => void handleCancelAfterSales(record)}
                    disabled={!record.systemNo}
                  >
                    <Button
                      type="link"
                      size="small"
                      danger
                      disabled={!record.systemNo}
                      loading={
                        record.systemNo != null &&
                        cancellingSystemNo === record.systemNo
                      }
                    >
                      取消售后
                    </Button>
                  </Popconfirm>
                  <Popconfirm
                    title="确定标记该订单售后完结？"
                    description="完结后订单仍保留在「售后完结」列表中。"
                    onConfirm={() => void handleCompleteAfterSales(record)}
                    disabled={!record.systemNo}
                  >
                    <Button
                      type="link"
                      size="small"
                      disabled={!record.systemNo}
                      loading={
                        record.systemNo != null &&
                        completingSystemNo === record.systemNo
                      }
                    >
                      售后完结
                    </Button>
                  </Popconfirm>
                </>
              )}
            </Space>
          );
        },
      },
    ],
    [
      cancellingSystemNo,
      completingSystemNo,
      handleCancelAfterSales,
      handleCompleteAfterSales,
    ],
  );

  const showEmpty = !loading && pageRows.length === 0;

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
          <Typography.Text type="secondary">分单日期</Typography.Text>
          <RangePicker
            size="middle"
            value={[dayjs(queryDateRange.start), dayjs(queryDateRange.end)]}
            onChange={(values) => void handleRangeFilterChange(values)}
            allowClear={false}
            format="YYYY-MM-DD"
          />
          <Typography.Text type="secondary">售后状态</Typography.Text>
          <Select<AfterSalesStatus>
            size="middle"
            value={afterSalesStatusFilter}
            options={AFTER_SALES_STATUS_OPTIONS}
            style={{ width: 120 }}
            onChange={(value) => setAfterSalesStatusFilter(value)}
          />
          <Input
            allowClear
            size="middle"
            prefix={<SearchOutlined />}
            placeholder="搜索商家 平台 系统编号 订单编号 收货人 电话"
            value={searchKeyword}
            onChange={(event) => setSearchKeyword(event.target.value)}
            style={{ width: 320 }}
          />
          {splitResult != null && (
            <Tag color={resolveStatusTagColor(afterSalesStatusFilter)}>
              {statusFilterLabel} {splitResult.totalRows} 条
            </Tag>
          )}
          <Button
            type="primary"
            size="middle"
            icon={<DownloadOutlined />}
            loading={exporting}
            onClick={() => void handleExport()}
          >
            售后信息导出
          </Button>
        </Space>
      </div>

      <div className="after-sales-table-panel">
        {loading && pageRows.length === 0 && (
          <div className="table-loading">
            <Spin size="large" tip="正在加载售后订单..." />
          </div>
        )}

        {showEmpty && (
          <div className="table-empty">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={`当前筛选条件下暂无${statusFilterLabel}订单`}
            />
          </div>
        )}

        {pageRows.length > 0 && (
          <Table
            rowKey={(row) => row.systemNo || `${row.orderNo}-${row.issueDate}`}
            bordered
            size="small"
            loading={loading}
            columns={columns}
            dataSource={pageRows}
            scroll={{ x: 1600, y: "calc(100vh - 220px)" }}
            pagination={{
              current: tablePage,
              pageSize: tablePageSize,
              total: pageRows.length,
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
        )}
      </div>

      <Modal
        title="售后订单详情"
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
            {resolveAfterSalesDetailItems(detailRow).map((item) => (
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
    </div>
  );
}
