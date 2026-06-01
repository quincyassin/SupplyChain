import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Button,
  DatePicker,
  Descriptions,
  Empty,
  Modal,
  Space,
  Spin,
  Table,
  Tabs,
  Typography,
  message,
} from "antd";
import { DatabaseOutlined, ReloadOutlined, UndoOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import dayjs, { Dayjs } from "dayjs";
import {
  ImportOrderArchivePreview,
  ImportedDateSummary,
  SplitTableRow,
  archiveOrders,
  fetchArchivedDateSummaries,
  fetchArchivedOrdersByDateRange,
  formatLocalDateKey,
  previewArchiveOrders,
  previewRestoreArchivedOrders,
  restoreArchivedOrders,
} from "../api/orderApi";

const { RangePicker } = DatePicker;

function formatCell(value: unknown): string {
  if (value == null || value === "") {
    return "—";
  }
  return String(value);
}

const browseColumns: ColumnsType<SplitTableRow> = [
  { title: "系统编号", dataIndex: "systemNo", width: 120, fixed: "left" },
  { title: "发单日期", dataIndex: "issueDate", width: 110 },
  { title: "平台", dataIndex: "platform", width: 100 },
  { title: "商家", dataIndex: "merchant", width: 100 },
  { title: "订单编号", dataIndex: "orderNo", width: 140 },
  { title: "名称", dataIndex: "productName", width: 160, ellipsis: true },
  { title: "规格", dataIndex: "spec", width: 100, ellipsis: true },
  { title: "数量", dataIndex: "quantity", width: 70 },
  {
    title: "回单",
    dataIndex: "receiptStatusLabel",
    width: 90,
    render: (_, row) => formatCell(row.receiptStatusLabel),
  },
  {
    title: "售后",
    dataIndex: "afterSalesStatusLabel",
    width: 90,
    render: (_, row) => formatCell(row.afterSalesStatusLabel),
  },
  { title: "物流单号", dataIndex: "logisticsNo", width: 140, ellipsis: true },
];

function PreviewStats({ preview }: { preview: ImportOrderArchivePreview | null }) {
  if (!preview) {
    return null;
  }
  return (
    <Descriptions size="small" column={2} bordered style={{ marginTop: 12 }}>
      <Descriptions.Item label="日期区间">{preview.beforeDate}</Descriptions.Item>
      <Descriptions.Item label="订单条数">{preview.orderCount}</Descriptions.Item>
      <Descriptions.Item label="需售后（未完结）">
        {preview.pendingAfterSalesCount}
      </Descriptions.Item>
      <Descriptions.Item label="售后已完结">
        {preview.completedAfterSalesCount}
      </Descriptions.Item>
    </Descriptions>
  );
}

export default function DataArchivePanel() {
  const [archiveRange, setArchiveRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [archivePreview, setArchivePreview] = useState<ImportOrderArchivePreview | null>(
    null,
  );
  const [archiveLoading, setArchiveLoading] = useState(false);
  const [archiving, setArchiving] = useState(false);

  const [restoreRange, setRestoreRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [restorePreview, setRestorePreview] = useState<ImportOrderArchivePreview | null>(
    null,
  );
  const [restoreLoading, setRestoreLoading] = useState(false);
  const [restoring, setRestoring] = useState(false);

  const [dateSummaries, setDateSummaries] = useState<ImportedDateSummary[]>([]);
  const [summariesLoading, setSummariesLoading] = useState(false);
  const [browseRange, setBrowseRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [browseRows, setBrowseRows] = useState<SplitTableRow[]>([]);
  const [browseLoading, setBrowseLoading] = useState(false);

  const loadSummaries = useCallback(async () => {
    setSummariesLoading(true);
    try {
      const summaries = await fetchArchivedDateSummaries();
      setDateSummaries(summaries);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "加载归档日期失败");
    } finally {
      setSummariesLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSummaries();
  }, [loadSummaries]);

  const handlePreviewArchive = async () => {
    if (!archiveRange) {
      message.warning("请选择归档日期区间");
      return;
    }
    setArchiveLoading(true);
    try {
      const preview = await previewArchiveOrders(
        formatLocalDateKey(archiveRange[0].toDate()),
        formatLocalDateKey(archiveRange[1].toDate()),
      );
      setArchivePreview(preview);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "预览失败");
    } finally {
      setArchiveLoading(false);
    }
  };

  const executeArchive = async () => {
    if (!archiveRange) {
      return;
    }
    setArchiving(true);
    try {
      const result = await archiveOrders(
        formatLocalDateKey(archiveRange[0].toDate()),
        formatLocalDateKey(archiveRange[1].toDate()),
      );
      message.success(result.message);
      setArchivePreview(null);
      setArchiveRange(null);
      await loadSummaries();
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "归档失败");
    } finally {
      setArchiving(false);
    }
  };

  const handleArchive = () => {
    if (!archivePreview || archivePreview.orderCount <= 0) {
      message.warning("请先预览并确认有可归档订单");
      return;
    }
    if (archivePreview.pendingAfterSalesCount > 0) {
      Modal.confirm({
        title: "仍有未完结售后订单",
        content: `将归档 ${archivePreview.orderCount} 条订单，其中需售后（未完结）${archivePreview.pendingAfterSalesCount} 条。归档后这些订单在售后页暂时不可见，恢复后可继续处理。确认继续？`,
        okText: "确认归档",
        cancelText: "取消",
        onOk: () => executeArchive(),
      });
      return;
    }
    Modal.confirm({
      title: "确认归档",
      content: `将把发单日期 ${archivePreview.beforeDate} 内的 ${archivePreview.orderCount} 条订单移至归档表，主表日常查询将不再显示。`,
      okText: "确认归档",
      cancelText: "取消",
      onOk: () => executeArchive(),
    });
  };

  const handlePreviewRestore = async () => {
    if (!restoreRange) {
      message.warning("请选择恢复日期区间");
      return;
    }
    setRestoreLoading(true);
    try {
      const preview = await previewRestoreArchivedOrders(
        formatLocalDateKey(restoreRange[0].toDate()),
        formatLocalDateKey(restoreRange[1].toDate()),
      );
      setRestorePreview(preview);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "预览失败");
    } finally {
      setRestoreLoading(false);
    }
  };

  const handleRestore = () => {
    if (!restoreRange || !restorePreview || restorePreview.orderCount <= 0) {
      message.warning("请先预览并确认有可恢复订单");
      return;
    }
    Modal.confirm({
      title: "确认恢复",
      content: `将把 ${restorePreview.orderCount} 条归档订单恢复到主表（${restorePreview.beforeDate}），恢复后在分单/回单/售后页正常可见。`,
      okText: "确认恢复",
      cancelText: "取消",
      onOk: async () => {
        setRestoring(true);
        try {
          const result = await restoreArchivedOrders(
            formatLocalDateKey(restoreRange[0].toDate()),
            formatLocalDateKey(restoreRange[1].toDate()),
          );
          message.success(result.message);
          setRestorePreview(null);
          setRestoreRange(null);
          await loadSummaries();
        } catch (err: unknown) {
          message.error(err instanceof Error ? err.message : "恢复失败");
        } finally {
          setRestoring(false);
        }
      },
    });
  };

  const handleBrowse = async (range?: [Dayjs, Dayjs]) => {
    const target = range ?? browseRange;
    if (!target) {
      message.warning("请选择浏览日期区间");
      return;
    }
    setBrowseLoading(true);
    try {
      const result = await fetchArchivedOrdersByDateRange(
        formatLocalDateKey(target[0].toDate()),
        formatLocalDateKey(target[1].toDate()),
      );
      setBrowseRows(result.pageRows ?? []);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "加载归档订单失败");
    } finally {
      setBrowseLoading(false);
    }
  };

  const summaryColumns: ColumnsType<ImportedDateSummary> = useMemo(
    () => [
      { title: "发单日期", dataIndex: "date", width: 140 },
      { title: "条数", dataIndex: "rowCount", width: 100 },
      {
        title: "操作",
        key: "action",
        render: (_, row) => (
          <Button
            type="link"
            size="small"
            onClick={() => {
              const day = dayjs(row.date);
              const range: [Dayjs, Dayjs] = [day, day];
              setBrowseRange(range);
              void handleBrowse(range);
            }}
          >
            查看
          </Button>
        ),
      },
    ],
    [],
  );

  return (
    <div>
      <Typography.Paragraph type="secondary">
        将历史订单物理移至归档表，主表仅保留日常数据，查询更快。归档不删除数据，可随时按日期区间恢复；售后状态随订单整行保留。
      </Typography.Paragraph>
      <Tabs
        items={[
          {
            key: "archive",
            label: "归档",
            children: (
              <Space direction="vertical" size="middle" style={{ width: "100%" }}>
                <Alert
                  type="info"
                  showIcon
                  message="归档规则"
                  description="选择开始与结束日期，该区间内（含起止日）的订单将移至归档表。"
                />
                <Space wrap>
                  <RangePicker
                    value={archiveRange}
                    onChange={(values) => {
                      if (values && values[0] && values[1]) {
                        setArchiveRange([values[0], values[1]]);
                      } else {
                        setArchiveRange(null);
                      }
                    }}
                    disabledDate={(current) =>
                      current ? current > dayjs().endOf("day") : false
                    }
                  />
                  <Button loading={archiveLoading} onClick={() => void handlePreviewArchive()}>
                    预览
                  </Button>
                  <Button
                    type="primary"
                    icon={<DatabaseOutlined />}
                    loading={archiving}
                    disabled={!archivePreview || archivePreview.orderCount <= 0}
                    onClick={handleArchive}
                  >
                    确认归档
                  </Button>
                </Space>
                <PreviewStats preview={archivePreview} />
              </Space>
            ),
          },
          {
            key: "browse",
            label: "查看归档",
            children: (
              <Spin spinning={summariesLoading || browseLoading}>
                <Space direction="vertical" size="middle" style={{ width: "100%" }}>
                  <Typography.Text type="secondary">已归档日期汇总（只读）</Typography.Text>
                  <Table<ImportedDateSummary>
                    size="small"
                    rowKey="date"
                    pagination={{ pageSize: 10 }}
                    dataSource={dateSummaries}
                    columns={summaryColumns}
                    locale={{ emptyText: <Empty description="暂无归档数据" /> }}
                  />
                  <Space wrap>
                    <RangePicker
                      value={browseRange}
                      onChange={(values) => {
                        if (values && values[0] && values[1]) {
                          setBrowseRange([values[0], values[1]]);
                        } else {
                          setBrowseRange(null);
                        }
                      }}
                    />
                    <Button onClick={() => void handleBrowse()}>查询</Button>
                    <Button icon={<ReloadOutlined />} onClick={() => void loadSummaries()}>
                      刷新汇总
                    </Button>
                  </Space>
                  <Table<SplitTableRow>
                    size="small"
                    rowKey="systemNo"
                    scroll={{ x: 1200, y: 360 }}
                    pagination={{ pageSize: 20 }}
                    dataSource={browseRows}
                    columns={browseColumns}
                    locale={{ emptyText: <Empty description="请选择日期后查询" /> }}
                  />
                </Space>
              </Spin>
            ),
          },
          {
            key: "restore",
            label: "恢复",
            children: (
              <Space direction="vertical" size="middle" style={{ width: "100%" }}>
                <Alert
                  type="warning"
                  showIcon
                  message="恢复后订单回到主表"
                  description="分单、回单、售后等页面将重新可见；若主表已有相同系统编号将拒绝恢复。"
                />
                <Space wrap>
                  <RangePicker value={restoreRange} onChange={(values) => {
                    if (values && values[0] && values[1]) {
                      setRestoreRange([values[0], values[1]]);
                    } else {
                      setRestoreRange(null);
                    }
                  }} />
                  <Button loading={restoreLoading} onClick={() => void handlePreviewRestore()}>
                    预览
                  </Button>
                  <Button
                    type="primary"
                    icon={<UndoOutlined />}
                    loading={restoring}
                    disabled={!restorePreview || restorePreview.orderCount <= 0}
                    onClick={handleRestore}
                  >
                    确认恢复
                  </Button>
                </Space>
                <PreviewStats preview={restorePreview} />
              </Space>
            ),
          },
        ]}
      />
    </div>
  );
}
