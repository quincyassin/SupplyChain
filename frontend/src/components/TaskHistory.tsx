import { useEffect, useState } from 'react';
import { Card, Table, Tag } from 'antd';
import { TaskItem, fetchTasks } from '../api/orderApi';

interface TaskHistoryProps {
  refreshKey: number;
}

const statusColor: Record<string, string> = {
  SUCCESS: 'success',
  FAILED: 'error',
  PROCESSING: 'processing',
  PENDING: 'default',
};

const statusLabel: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  PROCESSING: '处理中',
  PENDING: '待处理',
};

export default function TaskHistory({ refreshKey }: TaskHistoryProps) {
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const data = await fetchTasks();
        setTasks(data);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [refreshKey]);

  return (
    <Card title="处理历史（最近 20 条）">
      <Table
        rowKey="taskId"
        loading={loading}
        size="small"
        dataSource={tasks}
        pagination={false}
        columns={[
          { title: 'ID', dataIndex: 'taskId', width: 70 },
          { title: '文件名', dataIndex: 'originalFileName', ellipsis: true },
          {
            title: '操作',
            dataIndex: 'operationType',
            width: 80,
            render: (v: string) => (v === 'SPLIT' ? '分单' : v),
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (v: string) => (
              <Tag color={statusColor[v] || 'default'}>{statusLabel[v] ?? v}</Tag>
            ),
          },
          { title: '输入行', dataIndex: 'inputRowCount', width: 80 },
          { title: '商家数', dataIndex: 'merchantGroupCount', width: 80 },
          { title: '输出行', dataIndex: 'outputRowCount', width: 80 },
          { title: '说明', dataIndex: 'message', ellipsis: true },
          {
            title: '时间',
            dataIndex: 'createdAt',
            width: 180,
            render: (v: string) => (v ? new Date(v).toLocaleString('zh-CN') : '-'),
          },
        ]}
      />
    </Card>
  );
}
