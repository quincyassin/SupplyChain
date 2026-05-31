import { RefObject, useEffect, useState } from "react";

/** 表头 + 分页 + 边框预留高度 */
const TABLE_CHROME_HEIGHT = 108;

/**
 * 根据容器可用高度计算 Ant Design Table 的 scroll.y
 */
export function useTableBodyScrollY(
  containerRef: RefObject<HTMLElement | null>,
  chromeHeight = TABLE_CHROME_HEIGHT,
): number {
  const [scrollY, setScrollY] = useState(360);

  useEffect(() => {
    const element = containerRef.current;
    if (!element) {
      return;
    }

    const updateScrollY = () => {
      const next = Math.max(200, element.clientHeight - chromeHeight);
      setScrollY(next);
    };

    updateScrollY();
    const observer = new ResizeObserver(updateScrollY);
    observer.observe(element);
    window.addEventListener("resize", updateScrollY);

    return () => {
      observer.disconnect();
      window.removeEventListener("resize", updateScrollY);
    };
  }, [containerRef, chromeHeight]);

  return scrollY;
}
