CREATE TABLE synchro_task(
  task_name         VARCHAR2(100 CHAR) NOT NULL
, task_id           VARCHAR2(100 CHAR) NOT NULL
, creation_time     TIMESTAMP(9)
, CONSTRAINT synchro_task_pk PRIMARY KEY (task_name, task_id));