import { useState } from "react";
import { Button, Input, Space, Typography, message } from "antd";
import { activateLicense, type LicenseStatus } from "../api/licenseApi";

const { Text } = Typography;
const { TextArea } = Input;

interface LicenseActivateFormProps {
  onActivated: (status: LicenseStatus) => void;
}

export default function LicenseActivateForm({ onActivated }: LicenseActivateFormProps) {
  const [activationCode, setActivationCode] = useState("");
  const [activating, setActivating] = useState(false);

  async function handleActivate() {
    const trimmedCode = activationCode.trim();
    if (!trimmedCode) {
      message.warning("请输入激活码");
      return;
    }
    setActivating(true);
    try {
      const nextStatus = await activateLicense(trimmedCode);
      message.success("激活成功");
      setActivationCode("");
      onActivated(nextStatus);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "激活失败");
    } finally {
      setActivating(false);
    }
  }

  return (
    <Space direction="vertical" size="middle" style={{ width: "100%" }}>
      <div>
        <Text strong>激活码</Text>
        <TextArea
          rows={4}
          placeholder="粘贴管理员提供的激活码"
          value={activationCode}
          onChange={(event) => setActivationCode(event.target.value)}
          style={{ marginTop: 8 }}
        />
      </div>
      <Button type="primary" loading={activating} onClick={() => void handleActivate()}>
        激活
      </Button>
    </Space>
  );
}
