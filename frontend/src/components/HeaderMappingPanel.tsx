import type {
  ColumnMappingItem,
  ExcelHeader,
  OrderFieldMeta,
} from "../api/orderApi";
import { UNMAPPED_COLUMN_INDEX, ensureCompleteMapping } from "../api/orderApi";
import { useTableBodyScrollY } from "../hooks/useTableBodyScrollY";
import { useMemo, useRef } from "react";
import { Select, Switch, Table, Typography } from "antd";

interface HeaderMappingPanelProps {
  mapping: ColumnMappingItem[];
  excelHeaders: ExcelHeader[];
  fields: OrderFieldMeta[];
  onChange: (next: ColumnMappingItem[]) => void;
}

interface FieldMappingRow extends ColumnMappingItem {
  label: string;
  required: boolean;
}

/** 表头占用高度 */
const MAPPING_TABLE_CHROME_HEIGHT = 56;

const PRODUCT_NAME_FIELD_KEY = "productName";

function applyFieldEnabledChange(
  mapping: ColumnMappingItem[],
  fields: OrderFieldMeta[],
  excelHeaders: ExcelHeader[],
  fieldKey: string,
  enabled: boolean,
): ColumnMappingItem[] {
  return ensureCompleteMapping(mapping, fields, excelHeaders).map((item) => {
    if (item.fieldKey !== fieldKey) {
      return item;
    }
    if (enabled) {
      return { ...item, enabled: true };
    }
    return {
      ...item,
      enabled: false,
      sourceIndex: UNMAPPED_COLUMN_INDEX,
    };
  });
}

function applyFieldSourceChange(
  mapping: ColumnMappingItem[],
  fields: OrderFieldMeta[],
  excelHeaders: ExcelHeader[],
  fieldKey: string,
  sourceIndex: number | null,
): ColumnMappingItem[] {
  let next = ensureCompleteMapping(mapping, fields, excelHeaders);
  const normalizedIndex =
    sourceIndex == null ? UNMAPPED_COLUMN_INDEX : sourceIndex;

  if (normalizedIndex >= 0) {
    next = next.map((item) =>
      item.sourceIndex === normalizedIndex && item.fieldKey !== fieldKey
        ? {
            ...item,
            sourceIndex: UNMAPPED_COLUMN_INDEX,
            enabled: false,
          }
        : item,
    );
  }

  return next.map((item) => {
    if (item.fieldKey !== fieldKey) {
      return item;
    }
    if (normalizedIndex < 0) {
      return {
        ...item,
        sourceIndex: UNMAPPED_COLUMN_INDEX,
        enabled: false,
      };
    }
    return {
      ...item,
      sourceIndex: normalizedIndex,
      enabled: true,
    };
  });
}

export function hasMappedProductName(mapping: ColumnMappingItem[]): boolean {
  return mapping.some(
    (item) =>
      item.fieldKey === PRODUCT_NAME_FIELD_KEY &&
      item.enabled &&
      item.sourceIndex >= 0,
  );
}

export default function HeaderMappingPanel({
  mapping,
  excelHeaders,
  fields,
  onChange,
}: HeaderMappingPanelProps) {
  const tableAreaRef = useRef<HTMLDivElement>(null);
  const tableScrollY = useTableBodyScrollY(
    tableAreaRef,
    MAPPING_TABLE_CHROME_HEIGHT,
  );

  const rows = useMemo(() => {
    const completeMapping = ensureCompleteMapping(mapping, fields, excelHeaders);
    return fields.map((field) => {
      const item =
        completeMapping.find((entry) => entry.fieldKey === field.fieldKey) ??
        ({
          fieldKey: field.fieldKey,
          sourceIndex: UNMAPPED_COLUMN_INDEX,
          enabled: false,
          sortOrder: 0,
        } satisfies ColumnMappingItem);
      return {
        ...item,
        label: field.label,
        required: field.required,
      } satisfies FieldMappingRow;
    });
  }, [mapping, fields, excelHeaders]);

  const usedColumnIndexes = useMemo(() => {
    const indexes = new Set<number>();
    for (const row of rows) {
      if (row.enabled && row.sourceIndex >= 0) {
        indexes.add(row.sourceIndex);
      }
    }
    return indexes;
  }, [rows]);

  const updateFieldEnabled = (fieldKey: string, enabled: boolean) => {
    onChange(
      applyFieldEnabledChange(mapping, fields, excelHeaders, fieldKey, enabled),
    );
  };

  const updateFieldSource = (fieldKey: string, sourceIndex: number | null) => {
    onChange(
      applyFieldSourceChange(mapping, fields, excelHeaders, fieldKey, sourceIndex),
    );
  };

  return (
    <div className="header-mapping-table-area">
      <Typography.Paragraph type="secondary" className="config-panel-intro">
        请为各系统字段勾选启用并选择对应 Excel 列；「商品名称」必须映射到某一列，其余字段可选。
      </Typography.Paragraph>
      <div className="table-scroll-viewport" ref={tableAreaRef}>
        <Table<FieldMappingRow>
          rowKey="fieldKey"
          size="small"
          pagination={false}
          scroll={{ x: 520, y: tableScrollY }}
          dataSource={rows}
          columns={[
            {
              title: "系统字段",
              dataIndex: "label",
              width: 160,
              ellipsis: true,
              render: (label: string, record) => (
                <Typography.Text ellipsis={{ tooltip: label }}>
                  {record.required ? `${label}（必选）` : label}
                </Typography.Text>
              ),
            },
            {
              title: "启用",
              key: "enabled",
              width: 72,
              align: "center",
              render: (_, record) => (
                <Switch
                  size="small"
                  checked={record.required ? record.enabled || record.sourceIndex >= 0 : record.enabled}
                  disabled={record.required}
                  onChange={(checked) => updateFieldEnabled(record.fieldKey, checked)}
                />
              ),
            },
            {
              title: "Excel 列",
              key: "sourceIndex",
              render: (_, record) => {
                const columnOptions = excelHeaders.map((header) => ({
                  label: header.headerName,
                  value: header.columnIndex,
                  disabled:
                    usedColumnIndexes.has(header.columnIndex) &&
                    header.columnIndex !== record.sourceIndex,
                }));
                const selectEnabled = record.required || record.enabled;
                return (
                  <Select
                    style={{ width: "100%" }}
                    allowClear
                    placeholder="未选择"
                    disabled={!selectEnabled}
                    value={record.sourceIndex >= 0 ? record.sourceIndex : undefined}
                    options={columnOptions}
                    onChange={(value) =>
                      updateFieldSource(
                        record.fieldKey,
                        value == null ? null : Number(value),
                      )
                    }
                  />
                );
              },
            },
          ]}
        />
      </div>
    </div>
  );
}
