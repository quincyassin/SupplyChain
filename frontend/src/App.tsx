import { useCallback, useEffect, useState } from "react";
import { Alert, Layout, Menu, Spin, Typography } from "antd";
import {
  AccountBookOutlined,
  BookOutlined,
  CustomerServiceOutlined,
  DeleteOutlined,
  DollarOutlined,
  HomeOutlined,
  SettingOutlined,
} from "@ant-design/icons";
import OrderPage from "./pages/OrderPage";
import AfterSalesPage from "./pages/AfterSalesPage";
import RecycleBinPage from "./pages/RecycleBinPage";
import ReconcilePage from "./pages/ReconcilePage";
import ProductPricePage from "./pages/ProductPricePage";
import ConfigPage from "./pages/ConfigPage";
import UserManualPage from "./pages/UserManualPage";
import { fetchLicenseStatus, type LicenseStatus } from "./api/licenseApi";
import type { ManualModuleKey } from "./content/userManualSections";

const { Header, Content } = Layout;
const { Title } = Typography;

type AppMenuKey =
  | "orders"
  | "reconcile"
  | "after-sales"
  | "recycle-bin"
  | "product-prices"
  | "config"
  | "manual";

type BusinessMenuKey = Exclude<AppMenuKey, "manual">;

function resolveManualModuleKey(businessMenu: BusinessMenuKey): ManualModuleKey {
  if (businessMenu === "config") {
    return "header-mapping";
  }
  if (businessMenu === "recycle-bin") {
    return "orders";
  }
  return businessMenu;
}

export default function App() {
  const [activeMenu, setActiveMenu] = useState<AppMenuKey>("orders");
  const [lastBusinessMenu, setLastBusinessMenu] =
    useState<BusinessMenuKey>("orders");
  const [manualModuleKey, setManualModuleKey] =
    useState<ManualModuleKey>("orders");
  const [licenseStatus, setLicenseStatus] = useState<LicenseStatus | null>(
    null,
  );
  const [licenseLoading, setLicenseLoading] = useState(true);

  const loadLicenseStatus = useCallback(async () => {
    setLicenseLoading(true);
    try {
      const status = await fetchLicenseStatus();
      setLicenseStatus(status);
      if (status.enforced && !status.licensed) {
        setActiveMenu("config");
      }
      return status;
    } catch {
      setLicenseStatus(null);
      return null;
    } finally {
      setLicenseLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadLicenseStatus();
  }, [loadLicenseStatus]);

  const handleActivated = useCallback((status: LicenseStatus) => {
    setLicenseStatus(status);
  }, []);

  const needActivate = Boolean(
    licenseStatus?.enforced && !licenseStatus.licensed,
  );

  if (licenseLoading) {
    return (
      <div className="license-page">
        <Spin size="large" tip="正在检查授权状态..." />
      </div>
    );
  }

  return (
    <Layout className="app-layout">
      <Header className="app-header">
        <Menu
          className="app-top-menu"
          mode="horizontal"
          selectedKeys={[activeMenu]}
          items={[
            { key: "orders", icon: <HomeOutlined />, label: "首页" },
            { key: "reconcile", icon: <AccountBookOutlined />, label: "对账" },
            {
              key: "after-sales",
              icon: <CustomerServiceOutlined />,
              label: "售后",
            },
            {
              key: "recycle-bin",
              icon: <DeleteOutlined />,
              label: "回收站",
            },
            {
              key: "product-prices",
              icon: <DollarOutlined />,
              label: "商品价格维护",
            },
            { key: "config", icon: <SettingOutlined />, label: "系统配置" },
            { key: "manual", icon: <BookOutlined />, label: "使用手册" },
          ]}
          onClick={({ key }) => {
            const menuKey = key as AppMenuKey;
            if (menuKey === "manual") {
              setManualModuleKey(resolveManualModuleKey(lastBusinessMenu));
              setActiveMenu("manual");
              return;
            }
            setLastBusinessMenu(menuKey);
            setActiveMenu(menuKey);
          }}
        />
        <Title level={5} className="app-header-title">
          分单发单助手
        </Title>
      </Header>
      {needActivate ? (
        <Alert
          className="app-license-banner"
          type="warning"
          showIcon
          banner
          message="软件尚未激活，请前往「系统配置 → 软件授权」输入激活码"
        />
      ) : null}
      <Content
        className={
          activeMenu === "config" || activeMenu === "manual"
            ? "page-container page-container-config"
            : "page-container"
        }
      >
        {activeMenu === "orders" && <OrderPage />}
        {activeMenu === "reconcile" && <ReconcilePage />}
        {activeMenu === "after-sales" && <AfterSalesPage />}
        {activeMenu === "recycle-bin" && <RecycleBinPage />}
        {activeMenu === "product-prices" && <ProductPricePage />}
        {activeMenu === "config" && (
          <ConfigPage
            initialKey={needActivate ? "license" : undefined}
            onLicenseActivated={handleActivated}
          />
        )}
        {activeMenu === "manual" && (
          <UserManualPage initialModuleKey={manualModuleKey} />
        )}
      </Content>
    </Layout>
  );
}
