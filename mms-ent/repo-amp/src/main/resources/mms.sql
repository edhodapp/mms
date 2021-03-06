-- Create tables
create table organizations
(
  id bigserial primary key,
  orgId text not null,
  orgName text not null,
  constraint unique_organizations unique(orgId, orgName)
);
create index orgId on organizations(orgId);

create table projects
(
  id bigserial primary key,
  projectId text not null,
  orgId integer references organizations(id),
  name text not null,
  location text not null,
  constraint unique_projects unique(orgId, projectId)
);
create index projectIdIndex on projects(projectid);
