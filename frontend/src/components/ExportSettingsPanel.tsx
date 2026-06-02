import { useCallback, useEffect, useRef, useState } from "react";
import {
  Button,
  Form,
  Input,
  Radio,
  Space,
  Spin,
  Typography,
  message,
} from "antd";
import { FolderOpenOutlined } from "@ant-design/icons";
import {
  ExportMode,
  fetchExportSettings,
  pickExportDirectory,
  saveExportSettings,
} from "../api/orderApi";

interface ExportSettingsFormValues {
  mode: ExportMode;
  exportDirectory: string;
}

const modeOptions: Array<{
  value: ExportMode;
  label: string;
  description: string;
}> = [
  {
    value: "SERVER_DIRECTORY",
    label: "导出到本地目录",
    description:
      "分单、回单、对账 Excel 写入下方选择的目录，按 {年}/{月}/{日}/分单、{年}/{月}/{日}/回单、{年}/{月}/{日}/对账 组织。",
  },
  {
    value: "BROWSER_DOWNLOAD",
    label: "浏览器下载",
    description: "分单、回单、对账完成后，通过浏览器下载 Excel 或 ZIP 到本地。",
  },
];

export default function ExportSettingsPanel() {
  const [form] = Form.useForm<ExportSettingsFormValues>();
  const [loading, setLoading] = useState(true);
  const [switchingMode, setSwitchingMode] = useState(false);
  const [pickingDirectory, setPickingDirectory] = useState(false);
  const skipModePersistRef = useRef(true);
  const selectedMode = Form.useWatch("mode", form);

  const loadSettings = useCallback(async () => {
    skipModePersistRef.current = true;
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
      skipModePersistRef.current = false;
      setLoading(false);
    }
  }, [form]);

  useEffect(() => {
    void loadSettings();
  }, [loadSettings]);

  const persistSettings = useCallback(
    async (values: ExportSettingsFormValues) => {
      const saved = await saveExportSettings({
        mode: values.mode,
        exportDirectory: values.exportDirectory?.trim(),
      });
      form.setFieldsValue({
        mode: saved.mode,
        exportDirectory: saved.exportDirectory ?? "",
      });
      return saved;
    },
    [form],
  );

  const handleModeChange = async (mode: ExportMode) => {
    if (skipModePersistRef.current) {
      return;
    }
    setSwitchingMode(true);
    try {
      await persistSettings({
        mode,
        exportDirectory: form.getFieldValue("exportDirectory") ?? "",
      });
      message.success(
        mode === "SERVER_DIRECTORY"
          ? "已切换为导出到本地目录"
          : "已切换为浏览器下载",
      );
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "切换导出方式失败";
      message.error(msg);
      await loadSettings();
    } finally {
      setSwitchingMode(false);
    }
  };

  const handlePickDirectory = async () => {
    setPickingDirectory(true);
    try {
      const result = await pickExportDirectory();
      if (result.cancelled) {
        return;
      }
      if (!result.directory?.trim()) {
        message.warning("未选择有效目录");
        return;
      }
      const directory = result.directory.trim();
      form.setFieldValue("exportDirectory", directory);
      await persistSettings({
        mode: form.getFieldValue("mode"),
        exportDirectory: directory,
      });
      message.success("导出目录已保存");
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "选择文件夹失败";
      message.error(msg);
    } finally {
      setPickingDirectory(false);
    }
  };

  return (
    <Spin spinning={loading}>
      <Typography.Paragraph type="secondary">
        配置分单、回单、对账的 Excel
        导出方式。批量导出选中订单始终通过浏览器下载，不受此配置影响。
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
          <Radio.Group
            disabled={switchingMode}
            onChange={(event) => void handleModeChange(event.target.value)}
          >
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
          label="导出根目录"
          extra="点击「选择文件夹」在本机弹出目录窗口（macOS / Windows 均支持）；选择后将自动保存导出目录。"
          required={selectedMode === "SERVER_DIRECTORY"}
        >
          <Space.Compact style={{ width: "100%" }}>
            <Form.Item
              name="exportDirectory"
              noStyle
              rules={[
                {
                  validator: async (_, value: string | undefined) => {
                    if (form.getFieldValue("mode") !== "SERVER_DIRECTORY") {
                      return;
                    }
                    if (!value?.trim()) {
                      throw new Error("请选择导出根目录");
                    }
                  },
                },
              ]}
            >
              <Input
                readOnly
                placeholder="请点击右侧按钮选择文件夹"
                disabled={selectedMode !== "SERVER_DIRECTORY"}
                style={{ width: "calc(100% - 120px)", fontFamily: "monospace" }}
              />
            </Form.Item>
            <Button
              icon={<FolderOpenOutlined />}
              loading={pickingDirectory}
              disabled={selectedMode !== "SERVER_DIRECTORY"}
              onClick={() => void handlePickDirectory()}
              style={{ width: 120 }}
            >
              选择文件夹
            </Button>
          </Space.Compact>
        </Form.Item>
      </Form>
    </Spin>
  );
}
