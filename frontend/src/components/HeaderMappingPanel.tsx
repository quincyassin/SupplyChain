import type {
  ColumnMappingItem,
  ExcelHeader,
  OrderFieldMeta,
} from "../api/orderApi";
import { UNMAPPED_COLUMN_INDEX, ensureCompleteMapping } from "../api/orderApi";
import { useTableBodyScrollY } from "../hooks/useTableBodyScrollY";
import { useMemo, useRef } from "react";
import { Select, Table, Typography } from "antd";

interface HeaderMappingPanelProps {
  mapping: ColumnMappingItem[];
  excelHeaders: ExcelHeader[];
  fields: OrderFieldMeta[];
  onChange: (next: ColumnMappingItem[]) => void;
}

interface ExcelColumnMappingRow {
  columnIndex: number;
  headerName: string;
  fieldKey?: string;
}

/** 表头占用高度 */
const MAPPING_TABLE_CHROME_HEIGHT = 56;

const PRODUCT_NAME_FIELD_KEY = "productName";

function buildExcelColumnRows(
  mapping: ColumnMappingItem[],
  fields: OrderFieldMeta[],
  excelHeaders: ExcelHeader[],
): ExcelColumnMappingRow[] {
  const completeMapping = ensureCompleteMapping(mapping, fields, excelHeaders);
  return excelHeaders.map((header) => {
    const matched = completeMapping.find(
      (item) => item.enabled && item.sourceIndex === header.columnIndex,
    );
    return {
      columnIndex: header.columnIndex,
      headerName: header.headerName,
      fieldKey: matched?.fieldKey,
    };
  });
}

function applyExcelColumnFieldChange(
  mapping: ColumnMappingItem[],
  fields: OrderFieldMeta[],
  excelHeaders: ExcelHeader[],
  columnIndex: number,
  fieldKey: string | null,
): ColumnMappingItem[] {
  const completeMapping = ensureCompleteMapping(mapping, fields, excelHeaders);

  let next = completeMapping.map((item) => {
    if (item.sourceIndex === columnIndex) {
      return {
        ...item,
        sourceIndex: UNMAPPED_COLUMN_INDEX,
        enabled: false,
      };
    }
    if (fieldKey && item.fieldKey === fieldKey) {
      return {
        ...item,
        sourceIndex: UNMAPPED_COLUMN_INDEX,
        enabled: false,
      };
    }
    return item;
  });

  if (fieldKey) {
    next = next.map((item) =>
      item.fieldKey === fieldKey
        ? { ...item, sourceIndex: columnIndex, enabled: true }
        : item,
    );
  }

  return next;
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

  const rows = useMemo(
    () => buildExcelColumnRows(mapping, fields, excelHeaders),
    [mapping, fields, excelHeaders],
  );

  const usedFieldKeys = useMemo(() => {
    const keys = new Set<string>();
    for (const row of rows) {
      if (row.fieldKey) {
        keys.add(row.fieldKey);
      }
    }
    return keys;
  }, [rows]);

  const updateColumnField = (columnIndex: number, fieldKey: string | null) => {
    onChange(
      applyExcelColumnFieldChange(
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
        请为每个 Excel
        列选择对应的系统字段；「商品名称」必须映射到某一列，其余字段可选。
      </Typography.Paragraph>
      <div className="table-scroll-viewport" ref={tableAreaRef}>
        <Table<ExcelColumnMappingRow>
          rowKey="columnIndex"
          size="small"
          pagination={false}
          scroll={{ y: tableScrollY }}
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
              title: "系统字段",
              dataIndex: "fieldKey",
              render: (fieldKey: string | undefined, record) => {
                const fieldOptions = fields.map((field) => ({
                  label: field.required
                    ? `${field.label}（必选）`
                    : field.label,
                  value: field.fieldKey,
                  disabled:
                    usedFieldKeys.has(field.fieldKey) &&
                    field.fieldKey !== fieldKey,
                }));
                return (
                  <Select
                    style={{ width: "100%" }}
                    allowClear
                    placeholder="未选择"
                    value={fieldKey}
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
