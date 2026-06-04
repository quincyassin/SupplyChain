import { RefObject, useLayoutEffect, useState } from "react";

const MIN_BODY_HEIGHT = 120;
const DEFAULT_PAGINATION_HEIGHT = 32;
const DEFAULT_HEADER_HEIGHT = 39;
/** 表格内容与分页之间的留白 */
const PAGINATION_GAP = 8;
const RETRY_DELAYS_MS = [0, 50, 150, 400, 800];

export interface UseTableBodyScrollYOptions {
  /** 表格容器已挂载时为 true；异步加载场景下须等表格出现后再测量 */
  enabled?: boolean;
}

function resolveTableViewport(root: HTMLElement): HTMLElement {
  if (root.classList.contains("table-scroll-viewport")) {
    return root;
  }
  const nested = root.querySelector(".table-scroll-viewport");
  if (nested instanceof HTMLElement) {
    return nested;
  }
  return root;
}

function measureByGeometry(viewport: HTMLElement): number | null {
  const thead = viewport.querySelector(".ant-table-thead");
  const pagination = viewport.querySelector(".ant-table-pagination");
  if (!(thead instanceof HTMLElement) || !(pagination instanceof HTMLElement)) {
    return null;
  }

  const viewportRect = viewport.getBoundingClientRect();
  const theadRect = thead.getBoundingClientRect();
  const paginationRect = pagination.getBoundingClientRect();

  if (viewportRect.height < MIN_BODY_HEIGHT) {
    return null;
  }

  // 布局未稳定时（分页尚未落位）跳过，避免用错误值覆盖正确高度
  if (
    paginationRect.height <= 0 ||
    paginationRect.top <= theadRect.bottom ||
    paginationRect.bottom > viewportRect.bottom + 4
  ) {
    return null;
  }

  const stickyScroll = viewport.querySelector(".ant-table-sticky-scroll");
  const stickyHeight =
    stickyScroll instanceof HTMLElement && stickyScroll.offsetHeight > 0
      ? stickyScroll.offsetHeight
      : 0;

  const bodySpace =
    paginationRect.top - theadRect.bottom - stickyHeight - PAGINATION_GAP;
  if (bodySpace < MIN_BODY_HEIGHT) {
    return null;
  }

  return Math.floor(bodySpace);
}

function sumOccupiedHeightAboveViewport(viewport: HTMLElement): number {
  let occupied = 0;
  let sibling = viewport.previousElementSibling;
  while (sibling) {
    if (sibling instanceof HTMLElement) {
      occupied += sibling.getBoundingClientRect().height;
      const style = window.getComputedStyle(sibling);
      occupied += Number.parseFloat(style.marginTop) || 0;
      occupied += Number.parseFloat(style.marginBottom) || 0;
    }
    sibling = sibling.previousElementSibling;
  }
  return occupied;
}

function resolveMeasureHeight(viewport: HTMLElement): number {
  const panelBody = viewport.closest(".table-panel-body");
  if (panelBody instanceof HTMLElement && panelBody.clientHeight > 0) {
    const availableHeight =
      panelBody.clientHeight - sumOccupiedHeightAboveViewport(viewport);
    if (availableHeight > 0) {
      // viewport 尚未被 flex 撑开时，clientHeight 会跟随表格内容（偏小）
      if (viewport.clientHeight >= availableHeight - 2) {
        return viewport.clientHeight;
      }
      return availableHeight;
    }
  }

  const layoutPanel = viewport.closest(
    ".table-panel, .after-sales-table-panel, .config-panel-table-area",
  );
  if (layoutPanel instanceof HTMLElement && layoutPanel.clientHeight > 0) {
    const style = window.getComputedStyle(layoutPanel);
    const paddingTop = Number.parseFloat(style.paddingTop) || 0;
    const paddingBottom = Number.parseFloat(style.paddingBottom) || 0;
    const innerHeight = layoutPanel.clientHeight - paddingTop - paddingBottom;
    if (innerHeight > 0) {
      return innerHeight;
    }
  }

  return viewport.clientHeight;
}

function measureByContainer(viewport: HTMLElement): number | null {
  const height = resolveMeasureHeight(viewport);
  if (height < MIN_BODY_HEIGHT + DEFAULT_PAGINATION_HEIGHT + PAGINATION_GAP) {
    return null;
  }

  const pagination = viewport.querySelector(".ant-table-pagination");
  const thead = viewport.querySelector(".ant-table-thead");
  const paginationHeight =
    pagination instanceof HTMLElement
      ? pagination.offsetHeight
      : DEFAULT_PAGINATION_HEIGHT;
  const headerHeight =
    thead instanceof HTMLElement ? thead.offsetHeight : DEFAULT_HEADER_HEIGHT;

  const stickyScroll = viewport.querySelector(".ant-table-sticky-scroll");
  const stickyHeight =
    stickyScroll instanceof HTMLElement ? stickyScroll.offsetHeight : 0;

  return Math.max(
    MIN_BODY_HEIGHT,
    height - paginationHeight - PAGINATION_GAP - headerHeight - stickyHeight,
  );
}

function measureScrollY(viewport: HTMLElement): number | null {
  const byContainer = measureByContainer(viewport);
  const byGeometry = measureByGeometry(viewport);
  if (byContainer != null && byGeometry != null) {
    return Math.max(byContainer, byGeometry);
  }
  return byContainer ?? byGeometry;
}

/**
 * 表格 body 可滚动高度：优先按 flex 容器可用高度计算（无 Tab 时占满 panel-body；
 * 首页有平台/商家 Tab 时会扣除 Tab 高度），几何测量仅作补充。
 */
export function useTableBodyScrollY(
  containerRef: RefObject<HTMLElement | null>,
  options: UseTableBodyScrollYOptions = {},
): number {
  const enabled = options.enabled ?? true;
  const [scrollY, setScrollY] = useState(360);

  useLayoutEffect(() => {
    if (!enabled) {
      return;
    }

    let cancelled = false;
    let frameId = 0;
    const timeoutIds: number[] = [];
    const observedElements = new Set<Element>();
    const resizeObserver = new ResizeObserver(() => {
      scheduleUpdate();
    });

    const ensureObserve = (element: Element | null | undefined) => {
      if (element && !observedElements.has(element)) {
        resizeObserver.observe(element);
        observedElements.add(element);
      }
    };

    const updateScrollY = () => {
      if (cancelled) {
        return;
      }

      const root = containerRef.current;
      if (!root) {
        return;
      }

      const viewport = resolveTableViewport(root);
      ensureObserve(viewport);

      const layoutPanel = viewport.closest(
        ".table-panel, .after-sales-table-panel, .config-panel-table-area",
      );
      ensureObserve(layoutPanel);
      ensureObserve(viewport.closest(".table-panel-body"));
      ensureObserve(viewport.closest(".order-main-content"));
      ensureObserve(viewport.closest(".order-main-layout"));

      ensureObserve(viewport.querySelector(".ant-table-thead"));
      ensureObserve(viewport.querySelector(".ant-table-pagination"));

      const next = measureScrollY(viewport);
      if (next == null) {
        return;
      }

      setScrollY((previous) =>
        Math.abs(previous - next) <= 1 ? previous : next,
      );
    };

    const scheduleUpdate = () => {
      cancelAnimationFrame(frameId);
      frameId = requestAnimationFrame(() => {
        requestAnimationFrame(updateScrollY);
      });
    };

    scheduleUpdate();
    for (const delay of RETRY_DELAYS_MS) {
      timeoutIds.push(window.setTimeout(scheduleUpdate, delay));
    }

    window.addEventListener("resize", scheduleUpdate);
    window.visualViewport?.addEventListener("resize", scheduleUpdate);

    return () => {
      cancelled = true;
      cancelAnimationFrame(frameId);
      for (const id of timeoutIds) {
        window.clearTimeout(id);
      }
      resizeObserver.disconnect();
      window.removeEventListener("resize", scheduleUpdate);
      window.visualViewport?.removeEventListener("resize", scheduleUpdate);
    };
  }, [containerRef, enabled]);

  return scrollY;
}
