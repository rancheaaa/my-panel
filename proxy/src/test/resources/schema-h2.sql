create table if not exists rc_env (
  id bigint auto_increment primary key,
  env_name varchar(255) not null,
  env_desc varchar(255),
  create_by varchar(64),
  create_time timestamp,
  update_by varchar(64),
  update_time timestamp
);

create table if not exists rc_project (
  id bigint auto_increment primary key,
  project_name varchar(255) not null,
  project_desc varchar(255),
  create_by varchar(64),
  create_time timestamp,
  update_by varchar(64),
  update_time timestamp
);

create table if not exists rc_config (
  id bigint auto_increment primary key,
  env_id bigint not null,
  project_id bigint not null,
  config_key varchar(255) not null,
  config_value text,
  config_desc varchar(255),
  source char(1) default '0',
  create_by varchar(64),
  create_time timestamp,
  update_by varchar(64),
  update_time timestamp
);

create unique index if not exists idx_env_project_key on rc_config (env_id, project_id, config_key);

create table if not exists rc_node (
  id bigint auto_increment primary key,
  env_id bigint not null,
  project_id bigint not null,
  node_ip varchar(64) not null,
  node_port int not null,
  status char(1) default '0',
  last_refresh_time timestamp,
  create_by varchar(64),
  create_time timestamp,
  update_by varchar(64),
  update_time timestamp
);

create unique index if not exists idx_env_project_ip_port on rc_node (env_id, project_id, node_ip, node_port);

