import { useEffect, useMemo, useState } from "react";
import { Card, Layout, Menu, Typography } from "antd";
import {
  AccountBookOutlined,
  ApartmentOutlined,
  CustomerServiceOutlined,
  DatabaseOutlined,
  DollarOutlined,
  ExportOutlined,
  HomeOutlined,
  ReadOutlined,
  SafetyCertificateOutlined,
  ShopOutlined,
  TableOutlined,
} from "@ant-design/icons";
import type { ReactNode } from "react";
import ManualSectionView from "../components/ManualSectionView";
import {
  getManualSection,
  manualMenuKeys,
  type ManualModuleKey,
} from "../content/userManualSections";

const { Sider, Content } = Layout;
const { Title } = Typography;

const sectionIcons: Record<ManualModuleKey, ReactNode> = {
  orders: <HomeOutlined />,
  reconcile: <AccountBookOutlined />,
  "after-sales": <CustomerServiceOutlined />,
  "product-prices": <DollarOutlined />,
  "header-mapping": <TableOutlined />,
  "field-mapping": <ApartmentOutlined />,
  "merchant-config": <ShopOutlined />,
  "export-settings": <ExportOutlined />,
  "data-archive": <DatabaseOutlined />,
  license: <SafetyCertificateOutlined />,
};

interface UserManualPageProps {
  initialModuleKey?: ManualModuleKey;
}

export default function UserManualPage({
  initialModuleKey = "orders",
}: UserManualPageProps) {
  const [activeKey, setActiveKey] = useState<ManualModuleKey>(initialModuleKey);

  useEffect(() => {
    setActiveKey(initialModuleKey);
  }, [initialModuleKey]);

  const menuItems = useMemo(
    () =>
      manualMenuKeys.map((key) => {
        const section = getManualSection(key);
        return {
          key,
          icon: sectionIcons[key],
          label: section?.title ?? key,
        };
      }),
    [],
  );

  const activeSection = getManualSection(activeKey);

  return (
    <Layout className="config-layout manual-layout">
      <Sider width={200} className="config-sider manual-sider" theme="light">
        <div className="config-sider-title">
          <ReadOutlined /> 使用手册
        </div>
        <Menu
          mode="inline"
          selectedKeys={[activeKey]}
          items={menuItems}
          onClick={({ key }) => setActiveKey(key as ManualModuleKey)}
        />
      </Sider>
      <Content className="config-content manual-content">
        <Card bordered={false} className="config-page-card manual-page-card">
          {activeSection != null ? (
            <>
              <Title level={5} className="config-page-title">
                {activeSection.title}
              </Title>
              <div className="manual-content-body">
                <ManualSectionView section={activeSection} />
              </div>
            </>
          ) : null}
        </Card>
      </Content>
    </Layout>
  );
}
