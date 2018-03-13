DROP TABLE IF EXISTS url_id_map;
DROP TABLE IF EXISTS url;
DROP TABLE IF EXISTS id;

CREATE TABLE id (
  id    bigserial PRIMARY KEY,
  value varchar UNIQUE
);

ALTER TABLE id OWNER to postgres;

CREATE TABLE url (
    url_id bigserial PRIMARY KEY,
    url varchar UNIQUE,
    last_check_time varchar NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    state varchar NOT NULL DEFAULT 'Alive'::varchar,
    http_code integer NOT NULL DEFAULT 200,
    http_message varchar NOT NULL DEFAULT 'OK'::varchar
);

ALTER TABLE url OWNER to postgres;

CREATE TABLE url_id_map (
    url_id  bigint NOT NULL,
    id      bigint NOT NULL,
    CONSTRAINT pk_url_id PRIMARY KEY (url_id, id),
    CONSTRAINT id_fk FOREIGN KEY (id)
        REFERENCES public.id (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT url_fk FOREIGN KEY (url_id)
        REFERENCES public.url (url_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);

ALTER TABLE url_id_map OWNER to postgres;