import React, { useState, useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { 
  CloseOutlined, 
  ReloadOutlined, 
  ArrowLeftOutlined, 
  CloseCircleOutlined,
  StopOutlined
} from '@ant-design/icons';
import { getMenuData, flattenMenu } from '../../utils/menuUtils';
import './index.scss';

const TagsView = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const contextMenuRef = useRef(null);
  
  const [menuMap, setMenuMap] = useState([]);

  // Initialize menu map from localStorage
  useEffect(() => {
      const data = getMenuData();
      setMenuMap(flattenMenu(data));
  }, []);
  
  // Initialize with Home or from sessionStorage
  const [tags, setTags] = useState(() => {
      const stored = sessionStorage.getItem('tagsView');
      let initialTags = [];
      if (stored) {
          try {
              initialTags = JSON.parse(stored);
               // Filter out the old root path tag
               initialTags = initialTags.filter(t => t.key !== '/');
               // Ensure /index is not closable
               initialTags = initialTags.map(t => t.key === '/index' ? { ...t, closable: false } : t);
           } catch (e) {
              console.error('Failed to parse tagsView from sessionStorage', e);
          }
      }
      
      // Ensure Home tag exists
      if (!initialTags.some(t => t.key === '/index')) {
          initialTags.unshift({ key: '/index', label: '首页', path: '/index', closable: false });
      }
      
      return initialTags;
  });

  const [contextMenu, setContextMenu] = useState({
    visible: false,
    left: 0,
    top: 0,
    selectedTag: null
  });

  // Persist tags
  useEffect(() => {
      sessionStorage.setItem('tagsView', JSON.stringify(tags));
  }, [tags]);

  useEffect(() => {
    const pathname = location.pathname;
    
    // Check if tag exists using functional state to ensure freshness
    setTags(prevTags => {
        const isExist = prevTags.some(tag => tag.key === pathname);
        if (!isExist) {
            let label = '';
            
            // Try to find in menu
            const menuItem = menuMap.find(item => item.key === pathname);
            if (menuItem) {
                label = menuItem.label;
            } else if (pathname === '/user/profile') {
                label = '个人中心';
            }
            
            if (label) {
                return [...prevTags, { 
                    key: pathname, 
                    label: label, 
                    path: pathname,
                    closable: pathname !== '/index'
                }];
            }
        }
        return prevTags;
    });
  }, [location.pathname, menuMap]);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (contextMenu.visible && contextMenuRef.current && !contextMenuRef.current.contains(event.target)) {
        setContextMenu({ ...contextMenu, visible: false });
      }
    };

    document.addEventListener('click', handleClickOutside);
    return () => {
      document.removeEventListener('click', handleClickOutside);
    };
  }, [contextMenu]);

  const handleClose = (e, tagKey) => {
    e.stopPropagation();
    
    // Do not close if it's the last one or Home
    // Calculate new tags first
    let newTags = [];
    let targetIndex = -1;
    
    // We need to access current tags state. Since this is an event handler, 'tags' is fresh from render.
    targetIndex = tags.findIndex(tag => tag.key === tagKey);
    newTags = tags.filter(tag => tag.key !== tagKey);
    
    setTags(newTags);
    
    // If closing the currently active tag
    if (tagKey === location.pathname) {
      const prevTag = newTags[targetIndex - 1];
      if (prevTag) {
        navigate(prevTag.path);
      } else {
        navigate('/');
      }
    }
  };

  const handleClick = (path) => {
    navigate(path);
  };

  const handleContextMenu = (e, tag) => {
    e.preventDefault();
    setContextMenu({
      visible: true,
      left: e.clientX,
      top: e.clientY,
      selectedTag: tag
    });
  };

  const refreshPage = () => {
    // Reload the current page content
    // Since we don't have a redirect route, we can just reload the window for now
    // Or we can just re-navigate to the current path which might not trigger re-render if using standard Router
    // A better way in React apps without full reload is often to have a RefreshContext or key on Outlet
    // For simplicity and effectiveness:
    window.location.reload();
    setContextMenu({ ...contextMenu, visible: false });
  };

  const closeCurrent = () => {
    if (contextMenu.selectedTag) {
        // Use the existing logic by calling handleClose with a mock event
        // But handleClose expects an event with stopPropagation.
        // Let's just reuse the logic inside handleClose or refactor it.
        // Refactoring handleClose to be reusable is better.
        // But for minimal change, let's call a new internal close function.
        closeTag(contextMenu.selectedTag.key);
    }
    setContextMenu({ ...contextMenu, visible: false });
  };

  const closeOthers = () => {
    if (contextMenu.selectedTag) {
        const { key } = contextMenu.selectedTag;
        const newTags = tags.filter(tag => tag.key === key || !tag.closable);
        setTags(newTags);
        if (location.pathname !== key) {
            navigate(key);
        }
    }
    setContextMenu({ ...contextMenu, visible: false });
  };

  const closeLeft = () => {
    if (contextMenu.selectedTag) {
        const { key } = contextMenu.selectedTag;
        const index = tags.findIndex(tag => tag.key === key);
        // Keep non-closable tags and tags from index onwards
        // But wait, "Left" means everything to the left of current index.
        // If there are non-closable tags on the left (like Home), they stay.
        const newTags = tags.filter((tag, i) => i >= index || !tag.closable);
        setTags(newTags);
        
        // If active tag was closed (it was on the left), navigate to selected tag
        // Check if current location is in the new tags
        const isActiveStillThere = newTags.some(tag => tag.key === location.pathname);
        if (!isActiveStillThere) {
            navigate(key);
        }
    }
    setContextMenu({ ...contextMenu, visible: false });
  };

  const closeAll = () => {
    const newTags = tags.filter(tag => !tag.closable);
    setTags(newTags);
    // If active tag is closed, navigate to the last remaining tag (usually Home)
    const isActiveStillThere = newTags.some(tag => tag.key === location.pathname);
    if (!isActiveStillThere) {
        const lastTag = newTags[newTags.length - 1];
        if (lastTag) {
            navigate(lastTag.path);
        } else {
            navigate('/');
        }
    }
    setContextMenu({ ...contextMenu, visible: false });
  };

  // Refactored close logic to be reusable
  const closeTag = (tagKey) => {
    // Do not close if it's the last one or Home (handled by closable check usually, but good to be safe)
    const tag = tags.find(t => t.key === tagKey);
    if (tag && !tag.closable) return;

    let newTags = [];
    let targetIndex = -1;
    
    targetIndex = tags.findIndex(tag => tag.key === tagKey);
    newTags = tags.filter(tag => tag.key !== tagKey);
    
    setTags(newTags);
    
    // If closing the currently active tag
    if (tagKey === location.pathname) {
      const prevTag = newTags[targetIndex - 1];
      if (prevTag) {
        navigate(prevTag.path);
      } else {
        navigate('/');
      }
    }
  };

  // Wrap existing handleClose to use new closeTag
  const onTagClose = (e, tagKey) => {
      e.stopPropagation();
      closeTag(tagKey);
  }

  return (
    <div className="tags-view-container">
      <div className="tags-view-scroll">
        {tags.map((tag, index) => {
            const isActive = tag.key === location.pathname;
            // Re-find icon if not in tag object (for initial Home tag or persisted tags losing icon component)
            // Note: JSON.stringify loses React Elements (icons). So we must re-find icon from menuMap.
            const icon = menuMap.find(m => m.key === tag.key)?.icon;

            return (
                <div key={tag.key} className="tags-view-wrapper">
                    <div 
                        className={`tags-view-item ${isActive ? 'active' : ''}`}
                        onClick={() => handleClick(tag.path)}
                        onContextMenu={(e) => handleContextMenu(e, tag)}
                    >
                        {icon && <span className="tag-icon">{icon}</span>}
                        <span className="tag-title">{tag.label}</span>
                        {tag.closable && (
                            <span className="tag-close" onClick={(e) => onTagClose(e, tag.key)}>
                                <CloseOutlined />
                            </span>
                        )}
                    </div>
                </div>
            )
        })}
      </div>
      
      {contextMenu.visible && (
        <ul 
            className="tags-view-contextmenu" 
            style={{ left: contextMenu.left, top: contextMenu.top }}
            ref={contextMenuRef}
        >
            <li onClick={refreshPage}>
                <ReloadOutlined /> 刷新页面
            </li>
            <li onClick={closeCurrent} className={!contextMenu.selectedTag?.closable ? 'disabled' : ''}>
                <CloseOutlined /> 关闭当前
            </li>
            <li onClick={closeOthers}>
                <CloseCircleOutlined /> 关闭其他
            </li>
            <li onClick={closeLeft}>
                <ArrowLeftOutlined /> 关闭左侧
            </li>
            <li onClick={closeAll}>
                <StopOutlined /> 全部关闭
            </li>
        </ul>
      )}
    </div>
  );
};

export default TagsView;
