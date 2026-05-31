import {
  createElement,
  useCallback,
  useMemo,
  useState,
  type MouseEvent,
  type ThHTMLAttributes,
} from "react";
import type { ColumnType, ColumnsType } from "antd/es/table";
import type { TableProps } from "antd";

interface ResizableTitleProps extends ThHTMLAttributes<HTMLTableCellElement> {
  width?: number;
  onResize?: (width: number) => void;
}

/** 可拖拽调整列宽的表头单元格 */
function ResizableTitle({ width, onResize, ...restProps }: ResizableTitleProps) {
  if (!width || !onResize) {
    return createElement("th", restProps);
  }

  const onMouseDown = (event: MouseEvent<HTMLSpanElement>) => {
    event.preventDefault();
    event.stopPropagation();
    const startX = event.clientX;
    const startWidth = width;

    const onMouseMove = (moveEvent: globalThis.MouseEvent) => {
      onResize(Math.max(60, startWidth + moveEvent.clientX - startX));
    };

    const onMouseUp = () => {
      document.removeEventListener("mousemove", onMouseMove);
      document.removeEventListener("mouseup", onMouseUp);
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    };

    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
    document.addEventListener("mousemove", onMouseMove);
    document.addEventListener("mouseup", onMouseUp);
  };

  return createElement(
    "th",
    {
      ...restProps,
      style: { ...restProps.style, position: "relative" },
    },
    restProps.children,
    createElement("span", {
      className: "resizable-table-handle",
      onMouseDown,
    }),
  );
}

function resolveColumnKey<T>(column: ColumnType<T>, index: number): string {
  if (column.key != null) {
    return String(column.key);
  }
  const dataIndex = column.dataIndex;
  if (Array.isArray(dataIndex)) {
    return dataIndex.join(".");
  }
  if (dataIndex != null && dataIndex !== "") {
    return String(dataIndex);
  }
  return `col-${index}`;
}

export function useResizableColumns<T extends object>(
  columns: ColumnsType<T>,
): {
  resizableColumns: ColumnsType<T>;
  tableComponents: TableProps<T>["components"];
  scrollX: number;
} {
  const [widthMap, setWidthMap] = useState<Record<string, number>>({});

  const handleResize = useCallback((columnKey: string, nextWidth: number) => {
    setWidthMap((prev) => ({
      ...prev,
      [columnKey]: Math.max(60, nextWidth),
    }));
  }, []);

  const { resizableColumns, scrollX } = useMemo(() => {
    let totalWidth = 0;
    const nextColumns = columns.map((column, index) => {
      const columnKey = resolveColumnKey(column, index);
      const width = widthMap[columnKey] ?? column.width ?? 100;
      totalWidth += Number(width);
      return {
        ...column,
        width,
        onHeaderCell: () => ({
          width,
          onResize: (nextWidth: number) => handleResize(columnKey, nextWidth),
        }),
      };
    });
    return { resizableColumns: nextColumns, scrollX: totalWidth };
  }, [columns, widthMap, handleResize]);

  const tableComponents = useMemo<TableProps<T>["components"]>(
    () => ({
      header: {
        cell: ResizableTitle,
      },
    }),
    [],
  );

  return { resizableColumns, tableComponents, scrollX };
}
