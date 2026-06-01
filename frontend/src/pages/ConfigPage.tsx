import { useEffect, useState } from "react";
import { Card, Layout, Menu, Typography } from "antd";
import {
  SettingOutlined,
  ShopOutlined,
  TableOutlined,
  ExportOutlined,
  ApartmentOutlined,
  DatabaseOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";
import HeaderMappingConfig from "../components/HeaderMappingConfig";
import FieldMappingPanel from "../components/FieldMappingPanel";
import MerchantConfigPanel from "../components/MerchantConfigPanel";
import ExportSettingsPanel from "../components/ExportSettingsPanel";
import DataArchivePanel from "../components/DataArchivePanel";
import LicensePanel from "../components/LicensePanel";
import type { LicenseStatus } from "../api/licenseApi";

const { Sider, Content } = Layout;
const { Title } = Typography;

type ConfigMenuKey =
  | "header-mapping"
  | "field-mapping"
  | "merchant-config"
  | "export-settings"
  | "data-archive"
  | "license";

interface ConfigPageProps {
  initialKey?: ConfigMenuKey;
  onLicenseActivated?: (status: LicenseStatus) => void;
}

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
  {
    key: "data-archive",
    icon: <DatabaseOutlined />,
    label: "数据归档",
  },
  {
    key: "license",
    icon: <SafetyCertificateOutlined />,
    label: "软件授权",
  },
];

const menuTitles: Record<ConfigMenuKey, string> = {
  "header-mapping": "表头映射（平台）",
  "field-mapping": "字段映射",
  "merchant-config": "商家配置",
  "export-settings": "导出配置",
  "data-archive": "数据归档",
  license: "软件授权",
};

export default function ConfigPage({ initialKey, onLicenseActivated }: ConfigPageProps) {
  const [activeKey, setActiveKey] = useState<ConfigMenuKey>(initialKey ?? "header-mapping");

  useEffect(() => {
    if (initialKey) {
      setActiveKey(initialKey);
    }
  }, [initialKey]);

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
          {activeKey === "data-archive" && <DataArchivePanel />}
          {activeKey === "license" && (
            <LicensePanel onLicenseActivated={onLicenseActivated} />
          )}
        </Card>
      </Content>
    </Layout>
  );
}
