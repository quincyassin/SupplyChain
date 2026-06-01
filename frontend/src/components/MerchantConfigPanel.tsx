import { useCallback, useEffect, useRef, useState } from "react";
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from "antd";
import type { FormInstance } from "antd/es/form";
import { DeleteOutlined, EditOutlined, PlusOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import {
  MerchantConfigItem,
  createMerchantConfig,
  deleteMerchantConfig,
  fetchMerchantConfigs,
  updateMerchantConfig,
} from "../api/orderApi";
import { useTableBodyScrollY } from "../hooks/useTableBodyScrollY";

interface MerchantFormValues {
  name: string;
  keywordsText: string;
}

function parseKeywordsText(text: string): string[] {
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

function keywordsToText(keywords: string[] | null | undefined): string {
  if (!keywords || keywords.length === 0) {
    return "";
  }
  return keywords.join("\n");
}

function buildInitialValues(
  editing: MerchantConfigItem | null,
): MerchantFormValues {
  if (!editing) {
    return { name: "", keywordsText: "" };
  }
  return {
    name: editing.name,
    keywordsText: keywordsToText(editing.keywords),
  };
}

export default function MerchantConfigPanel() {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [items, setItems] = useState<MerchantConfigItem[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MerchantConfigItem | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const formRef = useRef<FormInstance<MerchantFormValues>>(null);
  const tableAreaRef = useRef<HTMLDivElement>(null);
  const tableScrollY = useTableBodyScrollY(tableAreaRef, {
    enabled: !loading,
  });

  const reload = useCallback(async () => {
    const list = await fetchMerchantConfigs();
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
    init();
  }, [reload]);

  const closeModal = () => {
    setModalOpen(false);
    setEditing(null);
  };

  const openCreate = () => {
    setEditing(null);
    setModalOpen(true);
  };

  const openEdit = (record: MerchantConfigItem) => {
    setEditing(record);
    setModalOpen(true);
  };

  const handleSave = async (values: MerchantFormValues) => {
    const keywords = parseKeywordsText(values.keywordsText);
    if (keywords.length === 0) {
      message.warning("请至少填写一个关键字");
      return;
    }
    setSaving(true);
    try {
      const payload = { name: values.name.trim(), keywords };
      if (editing) {
        await updateMerchantConfig(editing.id, payload);
        message.success("已更新");
      } else {
        await createMerchantConfig(payload);
        message.success("已新增");
        setPage(1);
      }
      closeModal();
      await reload();
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await formRef.current?.validateFields();
      if (values) {
        await handleSave(values);
      }
    } catch {
      // 校验未通过
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteMerchantConfig(id);
      message.success("已删除");
      await reload();
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "删除失败");
    }
  };

  const formKey = editing ? `edit-${editing.id}` : "create";
  const initialValues = buildInitialValues(editing);

  const columns: ColumnsType<MerchantConfigItem> = [
    {
      title: "商家名称",
      dataIndex: "name",
      width: 160,
      ellipsis: true,
    },
    {
      title: "匹配关键字",
      dataIndex: "keywords",
      ellipsis: true,
      render: (keywords: string[], record) => (
        <div
          className="merchant-keyword-tags merchant-keyword-tags--editable"
          title="点击编辑关键字"
          onClick={() => openEdit(record)}
        >
          {(keywords ?? []).length > 0 ? (
            keywords.map((kw) => <Tag key={kw}>{kw}</Tag>)
          ) : (
            <Typography.Text type="secondary">点击添加关键字</Typography.Text>
          )}
        </div>
      ),
    },
    {
      title: "操作",
      width: 140,
      fixed: "right",
      render: (_, record) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定删除该商家配置？"
            onConfirm={() => handleDelete(record.id)}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  if (loading) {
    return (
      <div className="config-panel-loading">
        <Spin tip="加载商家配置..." />
      </div>
    );
  }

  return (
    <div className="config-panel">
      <Typography.Paragraph type="secondary" className="config-panel-intro">
        配置商家名称与关键字。分单时根据 Excel
        中的「商品名称」列是否包含关键字，将订单归入对应商家。
        多个关键字命中时，优先匹配更长的关键字。
      </Typography.Paragraph>

      <div className="config-panel-toolbar">
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新增商家
        </Button>
      </div>

      <div className="config-panel-table-area" ref={tableAreaRef}>
        <div className="table-scroll-viewport">
          <Table
            rowKey="id"
            bordered
            size="middle"
            tableLayout="fixed"
            dataSource={items}
            columns={columns}
            scroll={{ x: "100%", y: tableScrollY }}
            locale={{ emptyText: "暂无商家配置，请点击新增" }}
            pagination={{
              current: page,
              pageSize,
              showSizeChanger: true,
              pageSizeOptions: ["10", "20", "50"],
              showTotal: (total) => `共 ${total} 条`,
              position: ["bottomRight"],
              onChange: (nextPage, nextSize) => {
                setPage(nextPage);
                setPageSize(nextSize);
              },
            }}
          />
        </div>
      </div>

      <Modal
        title={editing ? "编辑商家" : "新增商家"}
        open={modalOpen}
        onOk={handleModalOk}
        confirmLoading={saving}
        onCancel={closeModal}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        {modalOpen && (
          <Form
            key={formKey}
            ref={formRef}
            layout="vertical"
            initialValues={initialValues}
          >
            <Form.Item
              name="name"
              label="商家名称"
              rules={[{ required: true, message: "请输入商家名称" }]}
            >
              <Input placeholder="分单后 Tab 上显示的名称" />
            </Form.Item>
            <Form.Item
              name="keywordsText"
              label="匹配关键字"
              rules={[{ required: true, message: "请填写关键字" }]}
              extra="每行一个，或用逗号分隔；商品名称包含任一关键字即归入该商家"
            >
              <Input.TextArea rows={5} placeholder={"耐克\nAJ\nAir Max"} />
            </Form.Item>
          </Form>
        )}
      </Modal>
    </div>
  );
}
