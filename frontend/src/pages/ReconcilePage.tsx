import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Button,
  DatePicker,
  Empty,
  Select,
  Space,
  Spin,
  Tabs,
  Typography,
  message,
} from "antd";
import dayjs, { Dayjs } from "dayjs";
import { DownloadOutlined } from "@ant-design/icons";
import {
  downloadBlob,
  exportMerchantReconcile,
  exportPlatformReconcile,
  fetchImportedOrdersByDateRange,
  formatLocalDateKey,
  PENDING_SPLIT_MERCHANT,
} from "../api/orderApi";

const { RangePicker } = DatePicker;

/** 与首页一致：分单日期最多一年 */
const MAX_SPLIT_HISTORY_DAYS = 365;
const MAX_SPLIT_RANGE_SPAN_DAYS = 365;

type ReconcileMode = "merchant" | "platform";

interface DateRangeKey {
  start: string;
  end: string;
}

interface SelectOption {
  label: string;
  value: string;
  rowCount: number;
}

function createTodayDateRange(): DateRangeKey {
  const today = formatLocalDateKey();
  return { start: today, end: today };
}

function isSelectableSplitDate(value: Dayjs): boolean {
  const day = value.startOf("day");
  const today = dayjs().startOf("day");
  const earliest = today.subtract(MAX_SPLIT_HISTORY_DAYS - 1, "day");
  return !day.isBefore(earliest) && !day.isAfter(today);
}

function formatRangeLabel(range: DateRangeKey): string {
  return range.start === range.end
    ? range.start
    : `${range.start} ~ ${range.end}`;
}

function buildExportFilename(
  prefix: string,
  target: string,
  range: DateRangeKey,
): string {
  const rangeLabel =
    range.start === range.end ? range.start : `${range.start}_${range.end}`;
  const safeTarget = target.replace(/[\\/:*?"<>|]/g, "_");
  return `${prefix}_${safeTarget}_${rangeLabel}.xlsx`;
}

export default function ReconcilePage() {
  const [mode, setMode] = useState<ReconcileMode>("merchant");
  const [queryDateRange, setQueryDateRange] = useState<DateRangeKey>(() =>
    createTodayDateRange(),
  );
  const queryDateRangeRef = useRef(queryDateRange);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [errorAlert, setErrorAlert] = useState<string | null>(null);
  const [merchantOptions, setMerchantOptions] = useState<SelectOption[]>([]);
  const [platformOptions, setPlatformOptions] = useState<SelectOption[]>([]);
  const [selectedMerchant, setSelectedMerchant] = useState<string | null>(null);
  const [selectedPlatform, setSelectedPlatform] = useState<string | null>(null);

  useEffect(() => {
    queryDateRangeRef.current = queryDateRange;
  }, [queryDateRange]);

  const loadOptions = useCallback(async (range: DateRangeKey) => {
    setLoading(true);
    setErrorAlert(null);
    try {
      const result = await fetchImportedOrdersByDateRange(
        range.start,
        range.end,
      );
      const merchants = (result.merchantGroups ?? [])
        .filter(
          (group) =>
            group.merchant !== PENDING_SPLIT_MERCHANT &&
            group.merchant !== "未匹配" &&
            (group.rowCount ?? 0) > 0,
        )
        .sort((left, right) =>
          left.merchant.localeCompare(right.merchant, "zh-CN"),
        )
        .map((group) => ({
          label: `${group.merchant}（${group.rowCount ?? 0} 条）`,
          value: group.merchant,
          rowCount: group.rowCount ?? 0,
        }));
      const platforms = (result.platformSummaries ?? [])
        .filter((item) => (item.rowCount ?? 0) > 0)
        .sort((left, right) =>
          left.platform.localeCompare(right.platform, "zh-CN"),
        )
        .map((item) => ({
          label: `${item.platform}（${item.rowCount ?? 0} 条）`,
          value: item.platform,
          rowCount: item.rowCount ?? 0,
        }));
      setMerchantOptions(merchants);
      setPlatformOptions(platforms);
      setSelectedMerchant((prev) =>
        prev != null && merchants.some((item) => item.value === prev)
          ? prev
          : null,
      );
      setSelectedPlatform((prev) =>
        prev != null && platforms.some((item) => item.value === prev)
          ? prev
          : null,
      );
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "加载对账选项失败";
      setErrorAlert(msg);
      setMerchantOptions([]);
      setPlatformOptions([]);
      setSelectedMerchant(null);
      setSelectedPlatform(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOptions(queryDateRange);
  }, [loadOptions, queryDateRange]);

  const handleRangeChange = useCallback(
    (values: [Dayjs | null, Dayjs | null] | null) => {
      if (values == null || values[0] == null || values[1] == null) {
        return;
      }
      if (
        !isSelectableSplitDate(values[0]) ||
        !isSelectableSplitDate(values[1])
      ) {
        message.warning("分单日期仅可选择最近一年内的日期");
        return;
      }
      const start = values[0].format("YYYY-MM-DD");
      const end = values[1].format("YYYY-MM-DD");
      const rangeSpanDays =
        values[1].startOf("day").diff(values[0].startOf("day"), "day") + 1;
      if (rangeSpanDays > MAX_SPLIT_RANGE_SPAN_DAYS) {
        message.warning(`分单日期区间不能超过 ${MAX_SPLIT_RANGE_SPAN_DAYS} 天`);
        return;
      }
      if (
        queryDateRangeRef.current.start === start &&
        queryDateRangeRef.current.end === end
      ) {
        return;
      }
      setQueryDateRange({ start, end });
    },
    [],
  );

  const rangePickerValue = useMemo(
    (): [Dayjs, Dayjs] => [
      dayjs(queryDateRange.start),
      dayjs(queryDateRange.end),
    ],
    [queryDateRange],
  );

  const handleExport = async () => {
    const range = queryDateRangeRef.current;
    setExporting(true);
    setErrorAlert(null);
    try {
      if (mode === "merchant") {
        if (!selectedMerchant) {
          message.warning("请选择商家");
          return;
        }
        const blob = await exportMerchantReconcile({
          startDate: range.start,
          endDate: range.end,
          merchant: selectedMerchant,
        });
        downloadBlob(
          blob,
          buildExportFilename("商家对账", selectedMerchant, range),
        );
        message.success(`已导出商家「${selectedMerchant}」对账数据`);
        return;
      }
      if (!selectedPlatform) {
        message.warning("请选择平台");
        return;
      }
      const blob = await exportPlatformReconcile({
        startDate: range.start,
        endDate: range.end,
        platform: selectedPlatform,
      });
      downloadBlob(
        blob,
        buildExportFilename("平台对账", selectedPlatform, range),
      );
      message.success(`已导出平台「${selectedPlatform}」对账数据`);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "导出失败";
      setErrorAlert(msg);
    } finally {
      setExporting(false);
    }
  };

  const tabItems = [
    {
      key: "merchant",
      label: "商家对账",
      children: (
        <Space direction="vertical" size={12} style={{ width: "100%" }}>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            按商家导出对账 Excel，包含成本价、总计（成本价×数量+运费），不含平台、商家、供货价列。
          </Typography.Paragraph>
          <Space wrap align="center">
            <Typography.Text>选择商家</Typography.Text>
            <Select
              style={{ minWidth: 260 }}
              placeholder="请选择商家"
              value={selectedMerchant ?? undefined}
              options={merchantOptions}
              onChange={setSelectedMerchant}
              showSearch
              optionFilterProp="label"
              disabled={loading || merchantOptions.length === 0}
            />
          </Space>
          {!loading && merchantOptions.length === 0 && (
            <Empty description="当前日期区间内没有可分账的商家订单" />
          )}
        </Space>
      ),
    },
    {
      key: "platform",
      label: "平台对账",
      children: (
        <Space direction="vertical" size={12} style={{ width: "100%" }}>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            按平台导出对账 Excel，包含供货价、总计（供货价×数量+运费），不含平台、商家、成本价列。
          </Typography.Paragraph>
          <Space wrap align="center">
            <Typography.Text>选择平台</Typography.Text>
            <Select
              style={{ minWidth: 260 }}
              placeholder="请选择平台"
              value={selectedPlatform ?? undefined}
              options={platformOptions}
              onChange={setSelectedPlatform}
              showSearch
              optionFilterProp="label"
              disabled={loading || platformOptions.length === 0}
            />
          </Space>
          {!loading && platformOptions.length === 0 && (
            <Empty description="当前日期区间内没有可对账的平台订单" />
          )}
        </Space>
      ),
    },
  ];

  return (
    <div className="after-sales-page">
      {errorAlert != null && (
        <Alert
          type="error"
          showIcon
          message={errorAlert}
          closable
          onClose={() => setErrorAlert(null)}
        />
      )}

      <div className="toolbar">
        <Space wrap size="middle" align="center">
          <Typography.Text type="secondary">分单日期</Typography.Text>
          <RangePicker
            size="middle"
            value={rangePickerValue}
            allowClear={false}
            format="YYYY-MM-DD"
            disabledDate={(current) =>
              current == null ? true : !isSelectableSplitDate(current)
            }
            onChange={handleRangeChange}
          />
          <Typography.Text type="secondary">
            {formatRangeLabel(queryDateRange)}
          </Typography.Text>
          <Button
            type="primary"
            size="middle"
            icon={<DownloadOutlined />}
            loading={exporting}
            disabled={loading}
            onClick={() => void handleExport()}
          >
            导出 Excel
          </Button>
        </Space>
      </div>

      <div className="after-sales-table-panel reconcile-panel">
        {loading ? (
          <div className="table-loading">
            <Spin tip="正在加载对账选项..." />
          </div>
        ) : (
          <Tabs
            activeKey={mode}
            onChange={(key) => setMode(key as ReconcileMode)}
            items={tabItems}
          />
        )}
      </div>
    </div>
  );
}
