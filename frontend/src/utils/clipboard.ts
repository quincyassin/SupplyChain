/**
 * 复制文本到剪贴板（HTTP 等非安全上下文下会回退到 execCommand）
 */
export async function copyTextToClipboard(text: string): Promise<void> {
  const value = text.trim();
  if (!value) {
    throw new Error("复制内容为空");
  }

  if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(value);
      return;
    } catch {
      // 非 HTTPS 等场景下 Clipboard API 不可用，继续走回退方案
    }
  }

  const textarea = document.createElement("textarea");
  textarea.value = value;
  textarea.setAttribute("readonly", "true");
  textarea.style.position = "fixed";
  textarea.style.top = "0";
  textarea.style.left = "-9999px";
  document.body.appendChild(textarea);
  textarea.focus();
  textarea.select();
  textarea.setSelectionRange(0, value.length);

  try {
    const copied = document.execCommand("copy");
    if (!copied) {
      throw new Error("execCommand copy failed");
    }
  } finally {
    document.body.removeChild(textarea);
  }
}
