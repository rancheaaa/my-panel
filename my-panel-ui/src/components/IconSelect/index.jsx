import React, { useMemo } from 'react';
import { Select } from 'antd';
import * as Icons from '@ant-design/icons';

const IconSelect = ({ value, onChange, placeholder = '请选择图标' }) => {
  const iconOptions = useMemo(() => {
    return Object.keys(Icons)
      .filter(key => {
        // Filter out internal properties and ensure it's a valid icon component
        // Most Antd icons end with Outlined, Filled, or TwoTone
        return (
          key !== 'default' &&
          key !== 'createFromIconfontCN' &&
          key !== 'getTwoToneColor' &&
          key !== 'setTwoToneColor' &&
          key !== 'IconProvider' &&
          (key.endsWith('Outlined') || key.endsWith('Filled') || key.endsWith('TwoTone'))
        );
      })
      .map(key => {
        const Icon = Icons[key];
        return {
          label: (
            <div style={{ display: 'flex', alignItems: 'center' }}>
              <Icon style={{ marginRight: 8, fontSize: '16px' }} />
              <span>{key}</span>
            </div>
          ),
          value: key,
          // Add a plain text property for searching if needed, 
          // though Select searches 'value' by default which is the name here.
        };
      });
  }, []);

  // Custom filter option to search by icon name
  const filterOption = (input, option) => {
    return (option?.value || '').toLowerCase().includes(input.toLowerCase());
  };

  return (
    <Select
      showSearch
      value={value}
      onChange={onChange}
      placeholder={placeholder}
      allowClear
      style={{ width: '100%' }}
      filterOption={filterOption}
      options={iconOptions}
      // Use virtual scrolling for better performance with large lists
      virtual={true}
      // Ensure the selected value also shows the icon
      optionLabelProp="label"
    />
  );
};

export default IconSelect;
