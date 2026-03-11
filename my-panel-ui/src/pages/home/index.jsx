import React from 'react';
import { Card, Col, Row, Typography, Tag, Descriptions, Space, Divider } from 'antd';
import { 
  HeartTwoTone, 
  SmileTwoTone, 
  RocketTwoTone, 
  CodeTwoTone, 
  HddTwoTone, 
  ApiTwoTone,
  GithubOutlined
} from '@ant-design/icons';

const { Title, Paragraph, Link } = Typography;

const Index = () => {
  return (
    <div className="app-container">
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card bordered={false}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <Title level={3}>欢迎使用 My Panel 后台管理系统</Title>
                <Paragraph>
                  这是一个基于 React 19 和 Ant Design 6 构建的现代化后台管理系统。
                  <br />
                  采用最新的前端技术栈，提供极佳的用户体验和开发效率。
                </Paragraph>
              </div>
              <RocketTwoTone style={{ fontSize: '48px' }} twoToneColor="#1890ff" />
            </div>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title={<Space><CodeTwoTone />前端技术栈</Space>} bordered={false} style={{ height: '100%' }}>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="核心框架">
                <Tag color="blue">React 19</Tag>
                <Tag color="cyan">React DOM 19</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="UI 组件库">
                <Tag color="geekblue">Ant Design 6.x</Tag>
                <Tag color="purple">@ant-design/icons</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="路由管理">
                <Tag color="magenta">React Router DOM 7</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="构建工具">
                <Tag color="gold">Vite 7</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="状态/交互">
                <Tag color="orange">Framer Motion</Tag>
                <Tag color="volcano">NProgress</Tag>
                <Tag color="green">Screenfull</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="网络请求">
                <Tag color="red">Axios</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="CSS 预处理">
                <Tag color="pink">Sass</Tag>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title={<Space><ApiTwoTone twoToneColor="#eb2f96" />后端/模拟技术</Space>} bordered={false} style={{ height: '100%' }}>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="数据模拟">
                <Tag color="lime">Mock.js</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="开发环境">
                <Tag color="cyan">Node.js</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="代码规范">
                <Tag color="blue">ESLint</Tag>
              </Descriptions.Item>
            </Descriptions>
            
            <Divider orientation="left">项目特点</Divider>
            <Paragraph>
              <ul>
                <li><HeartTwoTone twoToneColor="#eb2f96" /> 采用 React 19 最新特性</li>
                <li><SmileTwoTone /> 清新现代的 UI 设计，支持深色侧边栏</li>
                <li><RocketTwoTone /> 响应式布局，支持 PC 和移动端</li>
                <li><HddTwoTone /> 完整的权限管理和动态路由实现</li>
              </ul>
            </Paragraph>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Index;
