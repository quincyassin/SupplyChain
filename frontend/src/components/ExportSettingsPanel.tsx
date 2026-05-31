import { useCallback, useEffect, useState } from "react";
import { Button, Form, Radio, Space, Spin, Typography, message } from "antd";
import { SaveOutlined } from "@ant-design/icons";
import {
  ExportMode,
  fetchExportSettings,
  saveExportSettings,
} from "../api/orderApi";

interface ExportSettingsFormValues {
  mode: ExportMode;
}

const modeOptions: Array<{ value: ExportMode; label: string; description: string }> =
  [
    {
      value: "SERVER_DIRECTORY",
      label: "导出到桌面 testData 目录",
      description:
        "按商家分单后，将 Excel 写入服务器用户桌面的 testData/{日期}/分单/ 目录（当前默认方式）。",
    },
    {
      value: "BROWSER_DOWNLOAD",
      label: "浏览器下载",
      description:
        "按商家分单后，自动将各商家 Excel 打包为 ZIP，通过浏览器下载到本地。",
    },
  ];

export default function ExportSettingsPanel() {
  const [form] = Form.useForm<ExportSettingsFormValues>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const loadSettings = useCallback(async () => {
    setLoading(true);
    try {
      const settings = await fetchExportSettings();
      form.setFieldsValue({ mode: settings.mode });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "加载导出配置失败";
      message.error(msg);
    } finally {
      setLoading(false);
    }
  }, [form]);

  useEffect(() => {
    void loadSettings();
  }, [loadSettings]);

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const saved = await saveExportSettings(values.mode);
      form.setFieldsValue({ mode: saved.mode });
      message.success("导出配置已保存");
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "保存导出配置失败";
      message.error(msg);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Spin spinning={loading}>
      <Typography.Paragraph type="secondary">
        配置「按商家分单」完成后的 Excel 导出方式。批量导出选中订单始终通过浏览器下载，不受此配置影响。
      </Typography.Paragraph>
      <Form form={form} layout="vertical" initialValues={{ mode: "SERVER_DIRECTORY" }}>
        <Form.Item
          name="mode"
          label="分单后导出方式"
          rules={[{ required: true, message: "请选择导出方式" }]}
        >
          <Radio.Group>
            <Space direction="vertical" size="middle">
              {modeOptions.map((option) => (
                <Radio key={option.value} value={option.value}>
                  <div>
                    <div>{option.label}</div>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {option.description}
                    </Typography.Text>
                  </div>
                </Radio>
              ))}
            </Space>
          </Radio.Group>
        </Form.Item>
        <Form.Item>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            onClick={() => void handleSave()}
          >
            保存配置
          </Button>
        </Form.Item>
      </Form>
    </Spin>
  );
}
