#!/usr/bin/env node
/**
 * 截取分单宝各功能页面，供产品介绍 PPT 使用。
 * @author huangxinsong
 */
import { chromium } from "playwright";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
// 允许在临时目录运行：优先用环境变量指定项目根目录
const ROOT = process.env.CAPTURE_ROOT || process.env.PRODUCT_ROOT
  ? path.resolve(process.env.CAPTURE_ROOT || process.env.PRODUCT_ROOT)
  : path.resolve(__dirname, "..");
const OUT_DIR = path.join(ROOT, "docs", "screenshots");
const BASE_URL = process.env.CAPTURE_BASE_URL || "http://localhost:5173";

fs.mkdirSync(OUT_DIR, { recursive: true });

async function clickMenu(page, label) {
  const item = page.locator(".app-top-menu .ant-menu-item", { hasText: label }).first();
  await item.click();
  await page.waitForTimeout(800);
}

async function clickConfigSub(page, label) {
  const item = page.locator(".config-sider .ant-menu-item", { hasText: label }).first();
  await item.click();
  await page.waitForTimeout(600);
}

async function shot(page, name) {
  const file = path.join(OUT_DIR, `${name}.png`);
  await page.screenshot({ path: file, fullPage: false });
  console.log("saved", file);
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({
    viewport: { width: 1440, height: 900 },
    locale: "zh-CN",
  });

  await page.goto(BASE_URL, { waitUntil: "networkidle", timeout: 60000 });
  // 等待授权检查结束
  await page.waitForSelector(".app-header, .license-page", { timeout: 30000 });
  await page.waitForTimeout(1500);

  // 若仍在 loading，再等一会
  const spinning = await page.locator(".ant-spin-spinning").count();
  if (spinning > 0) {
    await page.waitForTimeout(2000);
  }

  // 首页
  await clickMenu(page, "首页");
  await page.waitForTimeout(1000);
  await shot(page, "01-首页");

  // 对账
  await clickMenu(page, "对账");
  await page.waitForTimeout(1000);
  await shot(page, "02-对账");

  // 售后
  await clickMenu(page, "售后");
  await page.waitForTimeout(1000);
  await shot(page, "03-售后");

  // 回收站
  await clickMenu(page, "回收站");
  await page.waitForTimeout(1000);
  await shot(page, "04-回收站");

  // 商品价格维护
  await clickMenu(page, "商品价格维护");
  await page.waitForTimeout(1000);
  await shot(page, "05-商品价格维护");

  // 系统配置各子页
  await clickMenu(page, "系统配置");
  await page.waitForTimeout(800);

  await clickConfigSub(page, "表头映射");
  await shot(page, "06-表头映射");

  await clickConfigSub(page, "字段映射");
  await shot(page, "07-字段映射");

  await clickConfigSub(page, "商家配置");
  await shot(page, "08-商家配置");

  await clickConfigSub(page, "导出配置");
  await shot(page, "09-导出配置");

  await clickConfigSub(page, "数据归档");
  await shot(page, "10-数据归档");

  await clickConfigSub(page, "软件授权");
  await shot(page, "11-软件授权");

  // 使用手册
  await clickMenu(page, "使用手册");
  await page.waitForTimeout(1000);
  await shot(page, "12-使用手册");

  // 回到首页，尝试打开上传相关区域（按钮可见即可）
  await clickMenu(page, "首页");
  await page.waitForTimeout(800);
  // 高亮工具栏区域再截一张操作台特写
  await shot(page, "13-首页操作台");

  await browser.close();
  console.log("done");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
