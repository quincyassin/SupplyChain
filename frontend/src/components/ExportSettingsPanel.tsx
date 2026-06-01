import { useCallback, useEffect, useState } from "react";
import { Button, Form, Input, Radio, Space, Spin, Typography, message } from "antd";
import { SaveOutlined } from "@ant-design/icons";
import {
  ExportMode,
  fetchExportSettings,
  saveExportSettings,
} from "../api/orderApi";

interface ExportSettingsFormValues {
  mode: ExportMode;
  exportDirectory: string;
}

const modeOptions: Array<{ value: ExportMode; label: string; description: string }> =
  [
    {
      value: "SERVER_DIRECTORY",
      label: "导出到本地目录",
      description:
        "分单、回单、对账 Excel 写入下方配置的目录，按 {日期}/分单、{日期}/回单、{日期}/对账 组织。",
    },
    {
      value: "BROWSER_DOWNLOAD",
      label: "浏览器下载",
      description:
        "分单、回单、对账完成后，通过浏览器下载 Excel 或 ZIP 到本地。",
    },
  ];

export default function ExportSettingsPanel() {
  const [form] = Form.useForm<ExportSettingsFormValues>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const selectedMode = Form.useWatch("mode", form);

  const loadSettings = useCallback(async () => {
    setLoading(true);
    try {
      const settings = await fetchExportSettings();
      form.setFieldsValue({
        mode: settings.mode,
        exportDirectory: settings.exportDirectory ?? "",
      });
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
      const saved = await saveExportSettings({
        mode: values.mode,
        exportDirectory: values.exportDirectory?.trim(),
      });
      form.setFieldsValue({
        mode: saved.mode,
        exportDirectory: saved.exportDirectory ?? "",
      });
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
        配置分单、回单、对账的 Excel 导出方式。批量导出选中订单始终通过浏览器下载，不受此配置影响。
      </Typography.Paragraph>
      <Form
        form={form}
        layout="vertical"
        initialValues={{ mode: "SERVER_DIRECTORY", exportDirectory: "" }}
      >
        <Form.Item
          name="mode"
          label="导出方式"
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
        <Form.Item
          name="exportDirectory"
          label="导出根目录"
          extra="请填写本机绝对路径，例如 macOS：/Users/用户名/Desktop/testData；Windows：C:\\导出\\testData"
          rules={[
            {
              validator: async (_, value: string | undefined) => {
                if (form.getFieldValue("mode") !== "SERVER_DIRECTORY") {
                  return;
                }
                if (!value?.trim()) {
                  throw new Error("请填写导出根目录");
                }
              },
            },
          ]}
        >
          <Input
            placeholder="例如：/Users/用户名/Desktop/testData"
            disabled={selectedMode !== "SERVER_DIRECTORY"}
          />
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
