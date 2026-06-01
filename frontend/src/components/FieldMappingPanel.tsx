import { useCallback, useEffect, useRef, useState } from "react";
import {
  Button,
  Form,
  Input,
  Modal,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from "antd";
import type { FormInstance } from "antd/es/form";
import { EditOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import {
  FieldAliasConfigItem,
  fetchFieldAliasConfigs,
  saveFieldAliasConfig,
} from "../api/orderApi";
import { useTableBodyScrollY } from "../hooks/useTableBodyScrollY";

interface AliasFormValues {
  aliasesText: string;
}

/** 表头 + 分页占用高度 */
const FIELD_ALIAS_TABLE_CHROME_HEIGHT = 108;

function parseAliasesText(text: string): string[] {
  const parts = text.split(/[\n,，]/);
  const result: string[] = [];
  for (const part of parts) {
    const trimmed = part.trim();
    if (trimmed && !result.includes(trimmed)) {
      result.push(trimmed);
    }
  }
  return result;
}

function aliasesToText(aliases: string[] | null | undefined): string {
  if (!aliases || aliases.length === 0) {
    return "";
  }
  return aliases.join("\n");
}

function buildInitialValues(
  editing: FieldAliasConfigItem | null,
): AliasFormValues {
  if (!editing) {
    return { aliasesText: "" };
  }
  return {
    aliasesText: aliasesToText(editing.aliases),
  };
}

export default function FieldMappingPanel() {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [items, setItems] = useState<FieldAliasConfigItem[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<FieldAliasConfigItem | null>(null);
  const formRef = useRef<FormInstance<AliasFormValues>>(null);
  const tableAreaRef = useRef<HTMLDivElement>(null);
  const tableScrollY = useTableBodyScrollY(
    tableAreaRef,
    FIELD_ALIAS_TABLE_CHROME_HEIGHT,
  );

  const reload = useCallback(async () => {
    const list = await fetchFieldAliasConfigs();
    setItems(list);
  }, []);

  useEffect(() => {
    const init = async () => {
      setLoading(true);
      try {
        await reload();
      } catch (err: unknown) {
        message.error(err instanceof Error ? err.message : "加载失败");
      } finally {
        setLoading(false);
      }
    };
    void init();
  }, [reload]);

  const closeModal = () => {
    setModalOpen(false);
    setEditing(null);
  };

  const openEdit = (record: FieldAliasConfigItem) => {
    setEditing(record);
    setModalOpen(true);
  };

  const handleSave = async (values: AliasFormValues) => {
    if (!editing) {
      return;
    }
    setSaving(true);
    try {
      const aliases = parseAliasesText(values.aliasesText);
      await saveFieldAliasConfig(editing.fieldKey, aliases);
      await reload();
      closeModal();
      message.success(`已保存「${editing.label}」字段别名`);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const columns: ColumnsType<FieldAliasConfigItem> = [
    {
      title: "系统字段",
      dataIndex: "label",
      width: 140,
      render: (label: string) => (
        <Typography.Text strong>{label}</Typography.Text>
      ),
    },
    {
      title: "匹配别名",
      dataIndex: "aliases",
      render: (aliases: string[] | undefined, record) => (
        <div
          className="merchant-keyword-tags merchant-keyword-tags--editable"
          title="点击编辑别名"
          onClick={() => openEdit(record)}
        >
          {aliases && aliases.length > 0 ? (
            aliases.map((alias) => <Tag key={alias}>{alias}</Tag>)
          ) : (
            <Typography.Text type="secondary">点击添加别名</Typography.Text>
          )}
        </div>
      ),
    },
    {
      title: "操作",
      key: "action",
      width: 88,
      fixed: "right",
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          icon={<EditOutlined />}
          onClick={() => openEdit(record)}
        >
          编辑
        </Button>
      ),
    },
  ];

  return (
    <Spin spinning={loading} wrapperClassName="field-mapping-panel-wrap">
      <div className="config-panel field-mapping-panel">
        <Typography.Paragraph type="secondary" className="config-panel-intro">
          配置 Excel
          表头与系统字段的别名关系。导入时将优先按此处别名智能匹配，例如「收货人」可匹配
          「收件人」「姓名」等表头。点击匹配别名或右侧「编辑」均可修改。
        </Typography.Paragraph>

        <div
          ref={tableAreaRef}
          className="config-panel-table-area field-mapping-table-area"
        >
          <Table
            rowKey="fieldKey"
            bordered
            size="small"
            columns={columns}
            dataSource={items}
            pagination={false}
            scroll={{ y: tableScrollY * 2 }}
          />
        </div>
      </div>

      <Modal
        title={editing ? `编辑字段别名：${editing.label}` : "编辑字段别名"}
        open={modalOpen}
        onCancel={closeModal}
        onOk={() => formRef.current?.submit()}
        confirmLoading={saving}
        okText="保存"
        cancelText="取消"
        destroyOnClose
        width={520}
      >
        <Form
          key={editing?.fieldKey ?? "new"}
          ref={formRef}
          layout="vertical"
          initialValues={buildInitialValues(editing)}
          onFinish={(values) => void handleSave(values)}
        >
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
            系统字段「{editing?.label}
            」已自动参与匹配，此处只需填写额外别名。每行一个，或用逗号分隔。
          </Typography.Paragraph>
          <Form.Item name="aliasesText" label="别名列表">
            <Input.TextArea
              rows={8}
              placeholder={
                editing?.fieldKey === "receiver"
                  ? "例如：\n姓名\n收件人\n收货人姓名"
                  : "例如：\n别名1\n别名2"
              }
            />
          </Form.Item>
        </Form>
      </Modal>
    </Spin>
  );
}
