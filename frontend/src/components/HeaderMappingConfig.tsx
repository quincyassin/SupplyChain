import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
} from "react";
import {
  Alert,
  Button,
  Card,
  Input,
  List,
  Modal,
  Popconfirm,
  Space,
  Spin,
  Typography,
  message,
} from "antd";
import {
  DeleteOutlined,
  PlusOutlined,
  SaveOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import HeaderMappingPanel, {
  hasMappedProductName,
} from "./HeaderMappingPanel";
import {
  ColumnMappingItem,
  ExcelHeader,
  OrderFieldMeta,
  createPlatformTemplate,
  deletePlatformTemplate,
  ensureCompleteMapping,
  fetchPlatformTemplate,
  fetchPlatformTemplates,
  fetchOrderFields,
  savePlatformTemplate,
  suggestExcelHeaders,
} from "../api/orderApi";

export default function HeaderMappingConfig() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [loading, setLoading] = useState(true);
  const [headerLoading, setHeaderLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [selectedPlatform, setSelectedPlatform] = useState<string | null>(null);
  const [fields, setFields] = useState<OrderFieldMeta[]>([]);
  const [excelHeaders, setExcelHeaders] = useState<ExcelHeader[]>([]);
  const [mapping, setMapping] = useState<ColumnMappingItem[]>([]);
  const [templateFileName, setTemplateFileName] = useState<string | null>(null);
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [newPlatformName, setNewPlatformName] = useState("");
  const [detailLoading, setDetailLoading] = useState(false);
  const fieldsRef = useRef<OrderFieldMeta[]>([]);

  const reloadPlatformList = useCallback(async () => {
    const list = await fetchPlatformTemplates();
    setPlatforms(list.map((item) => item.platform));
  }, []);

  const loadPlatformDetail = useCallback(
    async (platform: string, fieldList?: OrderFieldMeta[]) => {
      const effectiveFields = fieldList ?? fieldsRef.current;
      setDetailLoading(true);
      try {
        const detail = await fetchPlatformTemplate(platform);
        const headers = detail.templateHeaders ?? [];
        setExcelHeaders(headers);
        setTemplateFileName(detail.templateFileName ?? null);
        setMapping(
          ensureCompleteMapping(detail.mapping ?? [], effectiveFields, headers),
        );
      } catch (err: unknown) {
        message.error(err instanceof Error ? err.message : "加载平台模板失败");
        setMapping([]);
        setExcelHeaders([]);
        setTemplateFileName(null);
      } finally {
        setDetailLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    fieldsRef.current = fields;
  }, [fields]);

  useEffect(() => {
    let cancelled = false;
    const init = async () => {
      setLoading(true);
      try {
        const fieldList = await fetchOrderFields();
        if (cancelled) {
          return;
        }
        setFields(fieldList);
        fieldsRef.current = fieldList;
        const list = await fetchPlatformTemplates();
        const names = list.map((item) => item.platform);
        if (cancelled) {
          return;
        }
        setPlatforms(names);
        if (names.length > 0) {
          const first = names[0];
          setSelectedPlatform(first);
          await loadPlatformDetail(first, fieldList);
        }
      } catch (err: unknown) {
        if (!cancelled) {
          message.error(err instanceof Error ? err.message : "加载失败");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    void init();
    return () => {
      cancelled = true;
    };
  }, [loadPlatformDetail]);

  const handleSelectPlatform = (platform: string) => {
    setSelectedPlatform(platform);
    void loadPlatformDetail(platform);
  };

  const handleAddPlatform = async () => {
    const name = newPlatformName.trim();
    if (!name) {
      message.warning("请输入平台名称");
      return;
    }
    if (platforms.includes(name)) {
      message.warning("该平台已存在");
      return;
    }
    try {
      await createPlatformTemplate(name);
      await reloadPlatformList();
      setSelectedPlatform(name);
      setMapping([]);
      setExcelHeaders([]);
      setTemplateFileName(null);
      setAddModalOpen(false);
      setNewPlatformName("");
      message.success(`平台「${name}」已保存到数据库`);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "新增失败");
    }
  };

  const handleDeletePlatform = async (platform: string) => {
    try {
      await deletePlatformTemplate(platform);
      message.success("已删除");
      await reloadPlatformList();
      setPlatforms((prev) => prev.filter((p) => p !== platform));
      if (selectedPlatform === platform) {
        setSelectedPlatform(null);
        setMapping([]);
        setExcelHeaders([]);
        setTemplateFileName(null);
      }
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "删除失败");
    }
  };

  const handleTemplateUpload = async (file: File) => {
    if (!selectedPlatform) {
      message.warning("请先选择或新增平台");
      return;
    }
    setHeaderLoading(true);
    try {
      const result = await suggestExcelHeaders(file);
      setExcelHeaders(result.headers);
      setMapping(
        ensureCompleteMapping(
          result.suggestedMapping,
          fields.length > 0 ? fields : result.fields,
          result.headers,
        ),
      );
      setTemplateFileName(file.name);
      message.success("已读取模板表头，请核对后保存");
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "读取表头失败");
    } finally {
      setHeaderLoading(false);
    }
  };

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const picked = event.target.files?.[0];
    event.target.value = "";
    if (picked) {
      handleTemplateUpload(picked);
    }
  };

  const handleSave = async () => {
    if (!selectedPlatform) {
      message.warning("请先选择平台");
      return;
    }
    if (mapping.length === 0 || excelHeaders.length === 0) {
      message.warning("请先上传模板 Excel 并完成映射");
      return;
    }
    if (!hasMappedProductName(mapping)) {
      message.warning("请为某一 Excel 列映射「商品名称」");
      return;
    }
    setSaving(true);
    try {
      await savePlatformTemplate(selectedPlatform, {
        mapping: mapping.map((item, index) => ({ ...item, sortOrder: index })),
        templateHeaders: excelHeaders,
        templateFileName: templateFileName ?? undefined,
      });
      message.success(`平台「${selectedPlatform}」模板已保存`);
      await reloadPlatformList();
      await loadPlatformDetail(selectedPlatform);
    } catch (err: unknown) {
      message.error(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="config-panel-loading">
        <Spin tip="加载配置..." />
      </div>
    );
  }

  return (
    <div className="config-panel merchant-template-config">
      <Typography.Paragraph type="secondary" className="config-panel-intro">
        按电商平台配置导入表头模板。上传订单时，系统会根据表头自动匹配平台模板解析
        Excel。
      </Typography.Paragraph>

      <div className="merchant-template-layout">
        <Card
          size="small"
          title="平台列表"
          className="merchant-template-list"
          extra={
            <Button
              type="link"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => setAddModalOpen(true)}
            >
              新增
            </Button>
          }
        >
          {platforms.length === 0 ? (
            <Typography.Text type="secondary">
              暂无平台，请点击新增
            </Typography.Text>
          ) : (
            <List
              size="small"
              dataSource={platforms}
              renderItem={(platform) => (
                <List.Item
                  className={
                    selectedPlatform === platform
                      ? "merchant-list-item-active"
                      : ""
                  }
                  onClick={() => handleSelectPlatform(platform)}
                  actions={[
                    <Popconfirm
                      key="del"
                      title={`确定删除平台「${platform}」的模板配置？`}
                      description="删除后需重新新增平台才能再次配置。若只是想更新表头或列映射，无需删除，直接重新上传 Excel 并保存即可覆盖原模板。"
                      okText="仍要删除"
                      cancelText="取消"
                      onConfirm={() => handleDeletePlatform(platform)}
                    >
                      <Button
                        type="text"
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={(e) => e.stopPropagation()}
                      />
                    </Popconfirm>,
                  ]}
                >
                  {platform}
                </List.Item>
              )}
            />
          )}
        </Card>

        <div className="merchant-template-editor">
          {!selectedPlatform ? (
            <Alert
              type="info"
              showIcon
              message="请从左侧选择一个平台，或新增平台"
            />
          ) : (
            <div className="merchant-template-editor-body">
              <Typography.Title level={5} style={{ marginTop: 0, flexShrink: 0 }}>
                {selectedPlatform}
              </Typography.Title>

              <input
                ref={fileInputRef}
                type="file"
                accept=".xlsx,.xls"
                style={{ display: "none" }}
                onChange={handleFileChange}
              />

              <Card size="small" className="merchant-template-editor-toolbar">
                <Space wrap>
                  <Button
                    icon={<UploadOutlined />}
                    loading={headerLoading}
                    onClick={() => fileInputRef.current?.click()}
                  >
                    上传模板 Excel
                  </Button>
                  {templateFileName && (
                    <Typography.Text type="secondary">
                      模板：{templateFileName}
                    </Typography.Text>
                  )}
                  <Button
                    type="primary"
                    icon={<SaveOutlined />}
                    loading={saving}
                    onClick={handleSave}
                    disabled={mapping.length === 0 || excelHeaders.length === 0}
                  >
                    保存该平台模板
                  </Button>
                </Space>
              </Card>

              {detailLoading ? (
                <div className="config-panel-loading">
                  <Spin tip="正在加载已保存的模板配置..." />
                </div>
              ) : excelHeaders.length === 0 ? (
                <Alert
                  type="warning"
                  showIcon
                  message="请上传该平台的模板 Excel"
                />
              ) : (
                <HeaderMappingPanel
                  mapping={mapping}
                  excelHeaders={excelHeaders}
                  fields={fields}
                  onChange={setMapping}
                />
              )}
            </div>
          )}
        </div>
      </div>

      <Modal
        title="新增平台"
        open={addModalOpen}
        onOk={handleAddPlatform}
        onCancel={() => {
          setAddModalOpen(false);
          setNewPlatformName("");
        }}
        okText="确定"
        cancelText="取消"
      >
        <Input
          placeholder="如：淘宝、拼多多、京东"
          value={newPlatformName}
          onChange={(e) => setNewPlatformName(e.target.value)}
          onPressEnter={handleAddPlatform}
        />
      </Modal>
    </div>
  );
}
