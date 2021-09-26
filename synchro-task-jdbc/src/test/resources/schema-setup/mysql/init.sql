CREATE TABLE synchro_task(
  task_name         VARCHAR(100) NOT NULL
, task_id           VARCHAR(100) NOT NULL
, creation_time     TIMESTAMP(6)
, CONSTRAINT synchro_task_pk PRIMARY KEY (task_name, task_id));