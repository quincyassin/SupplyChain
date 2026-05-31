import { useState } from "react";
import { Layout, Menu, Typography } from "antd";
import {
  AccountBookOutlined,
  CustomerServiceOutlined,
  HomeOutlined,
  SettingOutlined,
} from "@ant-design/icons";
import OrderPage from "./pages/OrderPage";
import AfterSalesPage from "./pages/AfterSalesPage";
import ReconcilePage from "./pages/ReconcilePage";
import ConfigPage from "./pages/ConfigPage";

const { Header, Content } = Layout;
const { Title } = Typography;

type AppMenuKey = "orders" | "reconcile" | "after-sales" | "config";

export default function App() {
  const [activeMenu, setActiveMenu] = useState<AppMenuKey>("orders");

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
            { key: "config", icon: <SettingOutlined />, label: "系统配置" },
          ]}
          onClick={({ key }) => setActiveMenu(key as AppMenuKey)}
        />
      </Header>
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
        {activeMenu === "config" && <ConfigPage />}
      </Content>
    </Layout>
  );
}
