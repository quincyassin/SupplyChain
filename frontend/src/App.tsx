import { useCallback, useEffect, useState } from "react";
import { Alert, Layout, Menu, Spin, Typography } from "antd";
import {
  AccountBookOutlined,
  CustomerServiceOutlined,
  DollarOutlined,
  HomeOutlined,
  SettingOutlined,
} from "@ant-design/icons";
import OrderPage from "./pages/OrderPage";
import AfterSalesPage from "./pages/AfterSalesPage";
import ReconcilePage from "./pages/ReconcilePage";
import ProductPricePage from "./pages/ProductPricePage";
import ConfigPage from "./pages/ConfigPage";
import { fetchLicenseStatus, type LicenseStatus } from "./api/licenseApi";

const { Header, Content } = Layout;
const { Title } = Typography;

type AppMenuKey = "orders" | "reconcile" | "after-sales" | "product-prices" | "config";

export default function App() {
  const [activeMenu, setActiveMenu] = useState<AppMenuKey>("orders");
  const [licenseStatus, setLicenseStatus] = useState<LicenseStatus | null>(null);
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

  const needActivate = Boolean(licenseStatus?.enforced && !licenseStatus.licensed);

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
        <Title level={5} className="app-header-title">
          分单发单助手
        </Title>
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
              key: "product-prices",
              icon: <DollarOutlined />,
              label: "商品价格维护",
            },
            { key: "config", icon: <SettingOutlined />, label: "系统配置" },
          ]}
          onClick={({ key }) => setActiveMenu(key as AppMenuKey)}
        />
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
          activeMenu === "config"
            ? "page-container page-container-config"
            : "page-container"
        }
      >
        {activeMenu === "orders" && <OrderPage />}
        {activeMenu === "reconcile" && <ReconcilePage />}
        {activeMenu === "after-sales" && <AfterSalesPage />}
        {activeMenu === "product-prices" && <ProductPricePage />}
        {activeMenu === "config" && (
          <ConfigPage
            initialKey={needActivate ? "license" : undefined}
            onLicenseActivated={handleActivated}
          />
        )}
      </Content>
    </Layout>
  );
}
