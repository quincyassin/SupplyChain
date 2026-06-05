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
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from "antd";
import dayjs, { Dayjs } from "dayjs";
import { DeleteOutlined, RollbackOutlined, SearchOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import {
  fetchRecycleBinOrders,
  formatLocalDateKey,
  purgeSelectedRecycleBinOrders,
  PENDING_SPLIT_MERCHANT,
  restoreSelectedRecycleBinOrders,
  SplitResult,
  SplitTableRow,
} from "../api/orderApi";
import { useTableBodyScrollY } from "../hooks/useTableBodyScrollY";

const { RangePicker } = DatePicker;

/** 回收站默认查询最近 30 天 */
const DEFAULT_RECYCLE_BIN_RANGE_DAYS = 30;

interface DateRangeKey {
  start: string;
  end: string;
}

function createDefaultDateRange(): DateRangeKey {
  const end = formatLocalDateKey();
  const startDate = new Date();
  startDate.setDate(startDate.getDate() - (DEFAULT_RECYCLE_BIN_RANGE_DAYS - 1));
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

function renderEllipsisCell(value: string | undefined) {
  const text = formatCellText(value);
  return (
    <Typography.Text ellipsis={{ tooltip: text }} style={{ maxWidth: "100%" }}>
      {text}
    </Typography.Text>
  );
}

function resolveRecycleBinDetailItems(row: SplitTableRow) {
  return [
    { label: "删除时间", value: formatCellText(row.deletedAt) },
    { label: "系统编号", value: formatCellText(row.systemNo) },
    { label: "订单编号", value: formatCellText(row.orderNo) },
    { label: "平台", value: formatCellText(row.platform) },
    { label: "商家", value: formatCellText(row.merchant) },
    { label: "商品名称", value: formatCellText(row.productName) },
    { label: "规格", value: formatCellText(row.spec) },
    { label: "数量", value: formatCellText(row.quantity) },
    { label: "收货人", value: formatCellText(row.receiver) },
    { label: "收货人电话", value: formatCellText(row.phone) },
    { label: "收货人地址", value: formatCellText(row.address) },
    { label: "发单日期", value: formatCellText(row.issueDate) },
    { label: "备注", value: formatCellText(row.remark) },
  ];
}

export default function RecycleBinPage() {
  const [queryDateRange, setQueryDateRange] = useState<DateRangeKey>(
    createDefaultDateRange,
  );
  const queryDateRangeRef = useRef(queryDateRange);
  const [searchKeyword, setSearchKeyword] = useState("");
  const searchKeywordRef = useRef(searchKeyword);
  const [splitResult, setSplitResult] = useState<SplitResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorAlert, setErrorAlert] = useState<string | null>(null);
  const [selectedSystemNos, setSelectedSystemNos] = useState<string[]>([]);
  const [restoring, setRestoring] = useState(false);
  const [purging, setPurging] = useState(false);
  const [restoringSystemNo, setRestoringSystemNo] = useState<string | null>(null);
  const [purgingSystemNo, setPurgingSystemNo] = useState<string | null>(null);
  const [detailRow, setDetailRow] = useState<SplitTableRow | null>(null);
  const [tablePage, setTablePage] = useState(1);
  const [tablePageSize, setTablePageSize] = useState(20);

  useEffect(() => {
    queryDateRangeRef.current = queryDateRange;
  }, [queryDateRange]);

  useEffect(() => {
    searchKeywordRef.current = searchKeyword;
  }, [searchKeyword]);

  const loadRecycleBinOrders = useCallback(
    async (range: DateRangeKey = queryDateRangeRef.current, keyword = "") => {
      setLoading(true);
      setErrorAlert(null);
      try {
        const result = await fetchRecycleBinOrders(
          range.start,
          range.end,
          keyword.trim() || undefined,
        );
        setSplitResult(result);
        setSelectedSystemNos([]);
        setTablePage(1);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : "加载回收站失败";
        setErrorAlert(msg);
        message.error(msg);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    void loadRecycleBinOrders(createDefaultDateRange(), "");
  }, [loadRecycleBinOrders]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadRecycleBinOrders(
        queryDateRangeRef.current,
        searchKeywordRef.current,
      );
    }, 300);
    return () => window.clearTimeout(timer);
  }, [searchKeyword, loadRecycleBinOrders]);

  const handleRangeFilterChange = useCallback(
    async (values: [Dayjs | null, Dayjs | null] | null) => {
      if (values == null || values[0] == null || values[1] == null) {
        return;
      }
      const range = {
        start: values[0].format("YYYY-MM-DD"),
        end: values[1].format("YYYY-MM-DD"),
      };
      setQueryDateRange(range);
      await loadRecycleBinOrders(range);
    },
    [loadRecycleBinOrders],
  );

  const handleRestoreSelected = useCallback(async () => {
    if (selectedSystemNos.length === 0) {
      message.warning("请先勾选要恢复的订单");
      return;
    }
    setRestoring(true);
    try {
      const result = await restoreSelectedRecycleBinOrders(selectedSystemNos);
      await loadRecycleBinOrders(queryDateRangeRef.current);
      message.success(result.message);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "恢复失败");
    } finally {
      setRestoring(false);
    }
  }, [selectedSystemNos, loadRecycleBinOrders]);

  const handlePurgeSelected = useCallback(async () => {
    if (selectedSystemNos.length === 0) {
      message.warning("请先勾选要彻底删除的订单");
      return;
    }
    setPurging(true);
    try {
      const result = await purgeSelectedRecycleBinOrders(selectedSystemNos);
      await loadRecycleBinOrders(queryDateRangeRef.current);
      message.success(result.message);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "彻底删除失败");
    } finally {
      setPurging(false);
    }
  }, [selectedSystemNos, loadRecycleBinOrders]);

  const handleRestoreOne = useCallback(
    async (row: SplitTableRow) => {
      if (!row.systemNo) {
        return;
      }
      setRestoringSystemNo(row.systemNo);
      try {
        const result = await restoreSelectedRecycleBinOrders([row.systemNo]);
        await loadRecycleBinOrders(queryDateRangeRef.current);
        message.success(result.message);
      } catch (err: unknown) {
        message.error(err instanceof Error ? err.message : "恢复失败");
      } finally {
        setRestoringSystemNo(null);
      }
    },
    [loadRecycleBinOrders],
  );

  const handlePurgeOne = useCallback(
    async (row: SplitTableRow) => {
      if (!row.systemNo) {
        return;
      }
      setPurgingSystemNo(row.systemNo);
      try {
        const result = await purgeSelectedRecycleBinOrders([row.systemNo]);
        await loadRecycleBinOrders(queryDateRangeRef.current);
        message.success(result.message);
      } catch (err: unknown) {
        message.error(err instanceof Error ? err.message : "彻底删除失败");
      } finally {
        setPurgingSystemNo(null);
      }
    },
    [loadRecycleBinOrders],
  );

  const pageRows = useMemo(() => {
    if (!splitResult?.pageRows) {
      return [];
    }
    return splitResult.pageRows.map((row) => ({
      ...row,
      merchant: row.merchant?.trim() ? row.merchant : PENDING_SPLIT_MERCHANT,
    }));
  }, [splitResult]);

  const tableScrollViewportRef = useRef<HTMLDivElement>(null);
  const tableScrollY = useTableBodyScrollY(tableScrollViewportRef, {
    enabled: pageRows.length > 0,
  });

  const columns = useMemo<ColumnsType<SplitTableRow>>(
    () => [
      {
        title: "删除时间",
        dataIndex: "deletedAt",
        width: 170,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "系统编号",
        dataIndex: "systemNo",
        width: 150,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "订单编号",
        dataIndex: "orderNo",
        width: 150,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "平台",
        dataIndex: "platform",
        width: 120,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "商家",
        dataIndex: "merchant",
        width: 120,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "商品名称",
        dataIndex: "productName",
        width: 180,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "发单日期",
        dataIndex: "issueDate",
        width: 170,
        ellipsis: true,
        render: (value: string | undefined) => renderEllipsisCell(value),
      },
      {
        title: "操作",
        key: "action",
        width: 220,
        fixed: "right",
        render: (_, record) => (
          <Space size={0} wrap>
            <Button
              type="link"
              size="small"
              onClick={() => setDetailRow(record)}
            >
              详情
            </Button>
            <Popconfirm
              title="确定恢复该订单？"
              description="恢复后订单将回到对应发单日期的订单列表。"
              onConfirm={() => void handleRestoreOne(record)}
              disabled={!record.systemNo}
            >
              <Button
                type="link"
                size="small"
                disabled={!record.systemNo}
                loading={
                  record.systemNo != null &&
                  restoringSystemNo === record.systemNo
                }
              >
                恢复
              </Button>
            </Popconfirm>
            <Popconfirm
              title="确定彻底删除该订单？"
              description="彻底删除后无法恢复，请谨慎操作。"
              onConfirm={() => void handlePurgeOne(record)}
              disabled={!record.systemNo}
            >
              <Button
                type="link"
                size="small"
                danger
                disabled={!record.systemNo}
                loading={
                  record.systemNo != null && purgingSystemNo === record.systemNo
                }
              >
                彻底删除
              </Button>
            </Popconfirm>
          </Space>
        ),
      },
    ],
    [handlePurgeOne, handleRestoreOne, purgingSystemNo, restoringSystemNo],
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
          <Typography.Text type="secondary">发单日期</Typography.Text>
          <RangePicker
            size="middle"
            value={[dayjs(queryDateRange.start), dayjs(queryDateRange.end)]}
            onChange={(values) => void handleRangeFilterChange(values)}
            allowClear={false}
            format="YYYY-MM-DD"
          />
          <Input
            allowClear
            size="middle"
            prefix={<SearchOutlined />}
            placeholder="搜索商家 平台 系统编号 订单编号"
            value={searchKeyword}
            onChange={(event) => setSearchKeyword(event.target.value)}
            style={{ width: 320 }}
          />
          {splitResult != null && (
            <Tag color="default">回收站 {splitResult.totalRows} 条</Tag>
          )}
          <Popconfirm
            title={`确定恢复选中的 ${selectedSystemNos.length} 条订单？`}
            description="恢复后订单将回到对应发单日期的订单列表。"
            onConfirm={() => void handleRestoreSelected()}
            disabled={selectedSystemNos.length === 0}
          >
            <Button
              size="middle"
              icon={<RollbackOutlined />}
              loading={restoring}
              disabled={selectedSystemNos.length === 0}
            >
              批量恢复
            </Button>
          </Popconfirm>
          <Popconfirm
            title={`确定彻底删除选中的 ${selectedSystemNos.length} 条订单？`}
            description="彻底删除后无法恢复，请谨慎操作。"
            onConfirm={() => void handlePurgeSelected()}
            disabled={selectedSystemNos.length === 0}
          >
            <Button
              size="middle"
              danger
              icon={<DeleteOutlined />}
              loading={purging}
              disabled={selectedSystemNos.length === 0}
            >
              批量彻底删除
            </Button>
          </Popconfirm>
        </Space>
      </div>

      <div className="after-sales-table-panel">
        {loading && pageRows.length === 0 && (
          <div className="table-loading">
            <Spin size="large" tip="正在加载回收站..." />
          </div>
        )}

        {showEmpty && (
          <div className="table-empty">
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="当前筛选条件下回收站暂无订单"
            />
          </div>
        )}

        {pageRows.length > 0 && (
          <div className="table-scroll-viewport" ref={tableScrollViewportRef}>
            <Table
              rowKey={(row) => row.systemNo || `${row.orderNo}-${row.issueDate}`}
              bordered
              size="small"
              loading={loading}
              columns={columns}
              dataSource={pageRows}
              rowSelection={{
                selectedRowKeys: selectedSystemNos,
                onChange: (keys) => setSelectedSystemNos(keys.map(String)),
              }}
              scroll={{ x: 1500, y: tableScrollY }}
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
          </div>
        )}
      </div>

      <Modal
        title="回收站订单详情"
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
            {resolveRecycleBinDetailItems(detailRow).map((item) => (
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
