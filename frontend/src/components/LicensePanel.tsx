import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Descriptions,
  Divider,
  Space,
  Spin,
  Typography,
  message,
} from "antd";
import { CopyOutlined, ReloadOutlined } from "@ant-design/icons";
import { fetchLicenseStatus, type LicenseStatus } from "../api/licenseApi";
import LicenseActivateForm from "./LicenseActivateForm";

const { Text, Paragraph } = Typography;

interface LicensePanelProps {
  onLicenseActivated?: (status: LicenseStatus) => void;
}

export default function LicensePanel({
  onLicenseActivated,
}: LicensePanelProps) {
  const [status, setStatus] = useState<LicenseStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [showRenewForm, setShowRenewForm] = useState(false);

  async function loadStatus() {
    setLoading(true);
    try {
      setStatus(await fetchLicenseStatus());
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : "加载授权信息失败",
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadStatus();
  }, []);

  async function handleCopyMachineId() {
    if (!status?.machineIdDisplay) {
      return;
    }
    try {
      await navigator.clipboard.writeText(status.machineIdDisplay);
      message.success("机器码已复制");
    } catch {
      message.error("复制失败，请手动复制机器码");
    }
  }

  function handleActivated(nextStatus: LicenseStatus) {
    setStatus(nextStatus);
    setShowRenewForm(false);
    onLicenseActivated?.(nextStatus);
  }

  return (
    <Space direction="vertical" size="large" style={{ width: "100%" }}>
      <Paragraph type="secondary">
        复制用户编号发送给管理员获取激活码，在下方粘贴激活码完成授权。
      </Paragraph>

      {!status?.enforced ? (
        <Alert
          type="info"
          showIcon
          message="当前环境未启用授权校验（开发模式）"
        />
      ) : null}

      <Spin spinning={loading}>
        <Descriptions bordered size="small" column={1}>
          <Descriptions.Item label="授权状态">
            {status?.licensed ? "已激活" : "未激活"}
          </Descriptions.Item>
          <Descriptions.Item label="平台">
            {status?.platform ?? "-"}
          </Descriptions.Item>
          <Descriptions.Item label="用户编号">
            <Space>
              <Text code>{status?.machineIdDisplay ?? "-"}</Text>
              <Button
                size="small"
                icon={<CopyOutlined />}
                disabled={!status?.machineIdDisplay}
                onClick={() => void handleCopyMachineId()}
              >
                复制
              </Button>
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="到期日">
            {status?.expireAt ?? "-"}
          </Descriptions.Item>
          <Descriptions.Item label="剩余天数">
            {status?.remainingDays != null ? `${status.remainingDays} 天` : "-"}
          </Descriptions.Item>
        </Descriptions>
      </Spin>

      <Divider orientation="left" plain>
        {status?.licensed ? "更新激活码" : "输入激活码"}
      </Divider>

      {!status?.licensed ? (
        <>
          {status?.message ? (
            <Alert type="warning" showIcon message={status.message} />
          ) : null}
          <LicenseActivateForm onActivated={handleActivated} />
        </>
      ) : showRenewForm ? (
        <>
          <LicenseActivateForm onActivated={handleActivated} />
          <Button onClick={() => setShowRenewForm(false)}>取消</Button>
        </>
      ) : (
        <Button type="primary" onClick={() => setShowRenewForm(true)}>
          更换激活码
        </Button>
      )}

      <Button icon={<ReloadOutlined />} onClick={() => void loadStatus()}>
        刷新状态
      </Button>
    </Space>
  );
}
