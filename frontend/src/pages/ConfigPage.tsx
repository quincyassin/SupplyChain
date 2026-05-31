import { useState } from "react";
import { Card, Layout, Menu, Typography } from "antd";
import {
  SettingOutlined,
  ShopOutlined,
  TableOutlined,
  ExportOutlined,
  ApartmentOutlined,
} from "@ant-design/icons";
import HeaderMappingConfig from "../components/HeaderMappingConfig";
import FieldMappingPanel from "../components/FieldMappingPanel";
import MerchantConfigPanel from "../components/MerchantConfigPanel";
import ExportSettingsPanel from "../components/ExportSettingsPanel";

const { Sider, Content } = Layout;
const { Title } = Typography;

type ConfigMenuKey =
  | "header-mapping"
  | "field-mapping"
  | "merchant-config"
  | "export-settings";

const menuItems = [
  {
    key: "header-mapping",
    icon: <TableOutlined />,
    label: "表头映射",
  },
  {
    key: "field-mapping",
    icon: <ApartmentOutlined />,
    label: "字段映射",
  },
  {
    key: "merchant-config",
    icon: <ShopOutlined />,
    label: "商家配置",
  },
  {
    key: "export-settings",
    icon: <ExportOutlined />,
    label: "导出配置",
  },
];

const menuTitles: Record<ConfigMenuKey, string> = {
  "header-mapping": "表头映射（平台）",
  "field-mapping": "字段映射",
  "merchant-config": "商家配置",
  "export-settings": "导出配置",
};

export default function ConfigPage() {
  const [activeKey, setActiveKey] = useState<ConfigMenuKey>("header-mapping");

  return (
    <Layout className="config-layout">
      <Sider width={200} className="config-sider" theme="light">
        <div className="config-sider-title">
          <SettingOutlined /> 系统配置
        </div>
        <Menu
          mode="inline"
          selectedKeys={[activeKey]}
          items={menuItems}
          onClick={({ key }) => setActiveKey(key as ConfigMenuKey)}
        />
      </Sider>
      <Content className="config-content">
        <Card bordered={false} className="config-page-card">
          <Title level={5} className="config-page-title">
            {menuTitles[activeKey]}
          </Title>
          {activeKey === "header-mapping" && <HeaderMappingConfig />}
          {activeKey === "field-mapping" && <FieldMappingPanel />}
          {activeKey === "merchant-config" && <MerchantConfigPanel />}
          {activeKey === "export-settings" && <ExportSettingsPanel />}
        </Card>
      </Content>
    </Layout>
  );
}
