import type {
  ColumnMappingItem,
  ExcelHeader,
  OrderFieldMeta,
} from "../api/orderApi";
import { UNMAPPED_COLUMN_INDEX, ensureCompleteMapping } from "../api/orderApi";
import { useTableBodyScrollY } from "../hooks/useTableBodyScrollY";
import { useEffect, useMemo, useRef, useState } from "react";
import { Select, Switch, Table, Typography } from "antd";

interface HeaderMappingPanelProps {
  mapping: ColumnMappingItem[];
  excelHeaders: ExcelHeader[];
  fields: OrderFieldMeta[];
  onChange: (next: ColumnMappingItem[]) => void;
}

interface ExcelColumnMappingRow {
  columnIndex: number;
  headerName: string;
  fieldKey: string | null;
  enabled: boolean;
}

const PRODUCT_NAME_FIELD_KEY = "productName";

function applyColumnFieldChange(
  mapping: ColumnMappingItem[],
  fields: OrderFieldMeta[],
  excelHeaders: ExcelHeader[],
  columnIndex: number,
  fieldKey: string | null,
): ColumnMappingItem[] {
  let next = ensureCompleteMapping(mapping, fields, excelHeaders);

  next = next.map((item) =>
    item.sourceIndex === columnIndex
      ? {
          ...item,
          sourceIndex: UNMAPPED_COLUMN_INDEX,
          enabled: false,
        }
      : item,
  );

  if (fieldKey) {
    next = next.map((item) =>
      item.fieldKey === fieldKey
        ? {
            ...item,
            sourceIndex: columnIndex,
            enabled: true,
          }
        : item,
    );
    // 同一字段改绑到新列时，解除其它字段对该列的占用
    next = next.map((item) =>
      item.fieldKey !== fieldKey &&
      item.enabled &&
      item.sourceIndex === columnIndex
        ? {
            ...item,
            sourceIndex: UNMAPPED_COLUMN_INDEX,
            enabled: false,
          }
        : item,
    );
  }

  return next;
}

function collectActiveColumnIndexes(items: ColumnMappingItem[]): Set<number> {
  const indexes = new Set<number>();
  for (const item of items) {
    if (item.enabled && item.sourceIndex >= 0) {
      indexes.add(item.sourceIndex);
    }
  }
  return indexes;
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
  const tableScrollY = useTableBodyScrollY(tableAreaRef);

  const completeMapping = useMemo(
    () => ensureCompleteMapping(mapping, fields, excelHeaders),
    [mapping, fields, excelHeaders],
  );

  const [activeColumns, setActiveColumns] = useState<Set<number>>(
    () => new Set(),
  );

  useEffect(() => {
    setActiveColumns(collectActiveColumnIndexes(completeMapping));
  }, [mapping, excelHeaders]);

  const rows = useMemo(() => {
    return excelHeaders.map((header) => {
      const mapped = completeMapping.find(
        (item) =>
          item.enabled && item.sourceIndex === header.columnIndex,
      );
      const enabled = activeColumns.has(header.columnIndex);
      return {
        columnIndex: header.columnIndex,
        headerName: header.headerName,
        fieldKey: enabled && mapped ? mapped.fieldKey : null,
        enabled,
      } satisfies ExcelColumnMappingRow;
    });
  }, [completeMapping, excelHeaders, activeColumns]);

  const usedFieldKeys = useMemo(() => {
    const keys = new Set<string>();
    for (const row of rows) {
      if (row.enabled && row.fieldKey) {
        keys.add(row.fieldKey);
      }
    }
    return keys;
  }, [rows]);

  const updateColumnEnabled = (columnIndex: number, enabled: boolean) => {
    setActiveColumns((prev) => {
      const next = new Set(prev);
      if (enabled) {
        next.add(columnIndex);
      } else {
        next.delete(columnIndex);
      }
      return next;
    });
    if (!enabled) {
      onChange(
        applyColumnFieldChange(
          mapping,
          fields,
          excelHeaders,
          columnIndex,
          null,
        ),
      );
    }
  };

  const updateColumnField = (columnIndex: number, fieldKey: string | null) => {
    if (fieldKey) {
      const previousIndex = mapping.find(
        (item) =>
          item.fieldKey === fieldKey &&
          item.enabled &&
          item.sourceIndex >= 0,
      )?.sourceIndex;
      setActiveColumns((prev) => {
        const next = new Set(prev);
        next.add(columnIndex);
        if (previousIndex != null && previousIndex !== columnIndex) {
          next.delete(previousIndex);
        }
        return next;
      });
    } else {
      setActiveColumns((prev) => {
        const next = new Set(prev);
        next.delete(columnIndex);
        return next;
      });
    }
    onChange(
      applyColumnFieldChange(
        mapping,
        fields,
        excelHeaders,
        columnIndex,
        fieldKey,
      ),
    );
  };

  return (
    <div className="header-mapping-table-area">
      <Typography.Paragraph type="secondary" className="config-panel-intro">
        左侧为模板 Excel 列，勾选启用后选择对应系统字段；「商品名称」必须映射到某一列，其余字段可选。
      </Typography.Paragraph>
      <div className="table-scroll-viewport" ref={tableAreaRef}>
        <Table<ExcelColumnMappingRow>
          rowKey="columnIndex"
          size="small"
          pagination={false}
          scroll={{ x: 520, y: tableScrollY }}
          dataSource={rows}
          columns={[
            {
              title: "Excel 列",
              dataIndex: "headerName",
              width: 220,
              ellipsis: true,
              render: (headerName: string) => (
                <Typography.Text ellipsis={{ tooltip: headerName }}>
                  {headerName}
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
                  checked={record.enabled}
                  onChange={(checked) =>
                    updateColumnEnabled(record.columnIndex, checked)
                  }
                />
              ),
            },
            {
              title: "系统字段",
              key: "fieldKey",
              render: (_, record) => {
                const fieldOptions = fields.map((field) => ({
                  label: field.required ? `${field.label}（必选）` : field.label,
                  value: field.fieldKey,
                  disabled:
                    record.enabled &&
                    usedFieldKeys.has(field.fieldKey) &&
                    field.fieldKey !== record.fieldKey,
                }));
                return (
                  <Select
                    style={{ width: "100%" }}
                    allowClear
                    placeholder="未映射"
                    disabled={!record.enabled}
                    value={record.fieldKey ?? undefined}
                    options={fieldOptions}
                    onChange={(value) =>
                      updateColumnField(
                        record.columnIndex,
                        value == null ? null : String(value),
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
