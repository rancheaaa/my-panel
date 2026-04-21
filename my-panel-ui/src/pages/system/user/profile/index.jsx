import React, { useState, useEffect } from 'react';
import { Card, Row, Col, Tabs, Form, Input, Button, Radio, Upload, message, List, Divider } from 'antd';
import { UserOutlined, PhoneOutlined, MailOutlined, SafetyOutlined, CalendarOutlined, ClusterOutlined } from '@ant-design/icons';
import { getUserProfile, updateUserProfile, updateUserPwd } from '../../../../api/user';
import { getSalt } from '../../../login/api';
import { encryptPassword } from '../../../../utils/crypto';
import './index.scss';

const Profile = () => {
    const [loading, setLoading] = useState(false);
    const [userInfo, setUserInfo] = useState({});
    const [activeTab, setActiveTab] = useState('1');
    const [form] = Form.useForm();
    const [pwdForm] = Form.useForm();

    useEffect(() => {
        fetchUserProfile();
    }, []);

    const fetchUserProfile = async () => {
        setLoading(true);
        try {
            const res = await getUserProfile();
            if (res.code === 200) {
                setUserInfo({
                    ...res.data.user,
                    roleGroup: res.data.roleGroup,
                    postGroup: res.data.postGroup
                });
                form.setFieldsValue(res.data.user);
            }
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    };

    const handleUpdateProfile = async (values) => {
        try {
            message.loading({ content: '正在保存...', key: 'updateProfile' });
            const res = await updateUserProfile(values);
            if (res.code === 200) {
                 message.success({ content: '修改成功', key: 'updateProfile' });
                 setUserInfo({ ...userInfo, ...values });
                 // Update localStorage for consistency
                 const stored = JSON.parse(localStorage.getItem('userInfo') || '{}');
                 localStorage.setItem('userInfo', JSON.stringify({ ...stored, ...values }));
            } else {
                 message.error({ content: res.msg || '修改失败', key: 'updateProfile' });
            }
        } catch (error) {
             console.error(error);
             message.error({ content: '修改失败', key: 'updateProfile' });
        }
    };

    const handleUpdatePwd = async (values) => {
        if (values.newPassword !== values.confirmPassword) {
            message.error('两次输入的密码不一致');
            return;
        }
        try {
            message.loading({ content: '正在保存...', key: 'updatePwd' });
            const username = userInfo.userName;
            let salt = userInfo.salt || '';
            try {
                const saltRes = await getSalt(username);
                salt = saltRes.data || '';
            } catch (e) {
                console.error("Failed to get salt", e);
            }
            const encryptedOldPwd = encryptPassword(values.oldPassword, salt);
            const encryptedNewPwd = encryptPassword(values.newPassword, salt);
            const res = await updateUserPwd(encryptedOldPwd, encryptedNewPwd, salt);
            if (res.code === 200) {
                 message.success({ content: '修改成功', key: 'updatePwd' });
                 pwdForm.resetFields();
            } else {
                 message.error({ content: res.msg || '修改失败', key: 'updatePwd' });
            }
        } catch (error) {
             console.error(error);
             message.error({ content: '修改失败', key: 'updatePwd' });
        }
    };

    const items = [
        {
            key: '1',
            label: '基本资料',
            children: (
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={handleUpdateProfile}
                    component="div"
                >
                    <Form.Item label="用户昵称" name="nickName" rules={[{ required: true, message: '请输入用户昵称' }]}>
                        <Input placeholder="请输入用户昵称" />
                    </Form.Item>
                    <Form.Item label="手机号码" name="phonenumber" rules={[
                        { required: true, message: '请输入手机号码' },
                        { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码' }
                    ]}>
                        <Input placeholder="请输入手机号码" maxLength={11} />
                    </Form.Item>
                    <Form.Item label="用户邮箱" name="email" rules={[
                        { required: true, message: '请输入邮箱' },
                        { type: 'email', message: '请输入正确的邮箱格式' }
                    ]}>
                        <Input placeholder="请输入用户邮箱" />
                    </Form.Item>
                    <Form.Item label="性别" name="sex">
                        <Radio.Group>
                            <Radio value="0">男</Radio>
                            <Radio value="1">女</Radio>
                        </Radio.Group>
                    </Form.Item>
                    <Form.Item>
                        <Button type="primary" htmlType="submit">保存</Button>
                        <Button style={{ marginLeft: 8 }} onClick={() => form.resetFields()}>重置</Button>
                    </Form.Item>
                </Form>
            )
        },
        {
            key: '2',
            label: '修改密码',
            children: (
                <Form
                    form={pwdForm}
                    layout="vertical"
                    onFinish={handleUpdatePwd}
                    component="div"
                >
                    <Form.Item label="旧密码" name="oldPassword" rules={[{ required: true, message: '请输入旧密码' }]}>
                        <Input.Password placeholder="请输入旧密码" />
                    </Form.Item>
                    <Form.Item label="新密码" name="newPassword" rules={[
                        { required: true, message: '请输入新密码' },
                        { min: 6, message: '密码长度不能少于6位' }
                    ]}>
                        <Input.Password placeholder="请输入新密码" />
                    </Form.Item>
                    <Form.Item label="确认密码" name="confirmPassword" rules={[
                        { required: true, message: '请确认新密码' },
                        ({ getFieldValue }) => ({
                            validator(_, value) {
                                if (!value || getFieldValue('newPassword') === value) {
                                    return Promise.resolve();
                                }
                                return Promise.reject(new Error('两次输入的密码不一致'));
                            },
                        }),
                    ]}>
                        <Input.Password placeholder="请确认新密码" />
                    </Form.Item>
                    <Form.Item>
                        <Button type="primary" htmlType="submit">保存</Button>
                        <Button style={{ marginLeft: 8 }} onClick={() => pwdForm.resetFields()}>重置</Button>
                    </Form.Item>
                </Form>
            )
        }
    ];

    return (
        <div className="app-container">
            <Row gutter={24} wrap={false}>
                <Col span={8}>
                    <Card className="box-card" title="个人信息">
                        <div className="text-center">
                            <div className="user-avatar-box">
                                <UserOutlined style={{ fontSize: '64px', color: '#1890ff', background: '#e6f7ff', padding: '20px', borderRadius: '50%' }} />
                            </div>
                        </div>
                        <ul className="list-group list-group-striped">
                            <li className="list-group-item">
                                <div>
                                    <UserOutlined style={{ marginRight: 5 }} />用户名称
                                </div>
                                <div className="pull-right">{userInfo.userName}</div>
                            </li>
                            <li className="list-group-item">
                                <div>
                                    <PhoneOutlined style={{ marginRight: 5 }} />手机号码
                                </div>
                                <div className="pull-right">{userInfo.phonenumber}</div>
                            </li>
                            <li className="list-group-item">
                                <div>
                                    <MailOutlined style={{ marginRight: 5 }} />用户邮箱
                                </div>
                                <div className="pull-right">{userInfo.email}</div>
                            </li>
                            <li className="list-group-item">
                                <div>
                                    <ClusterOutlined style={{ marginRight: 5 }} />所属部门
                                </div>
                                <div className="pull-right">
                                    {[userInfo.deptName || (userInfo.dept && userInfo.dept.deptName), userInfo.roleGroup].filter(Boolean).join(' / ')}
                                </div>
                            </li>
                            <li className="list-group-item">
                                <div>
                                    <CalendarOutlined style={{ marginRight: 5 }} />创建日期
                                </div>
                                <div className="pull-right">{userInfo.createTime}</div>
                            </li>
                        </ul>
                    </Card>
                </Col>
                <Col span={12}>
                    <Card className="box-card" title="基本资料">
                        <Tabs defaultActiveKey="1" items={items} />
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default Profile;
