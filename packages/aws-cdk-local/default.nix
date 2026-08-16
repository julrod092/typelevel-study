{
  buildNpmPackage,
  fetchurl,
  nodejs_24,
}:
buildNpmPackage rec {
  pname = "aws-cdk-local";
  version = "3.0.4";

  src = fetchurl {
    url = "https://registry.npmjs.org/${pname}/-/${pname}-${version}.tgz";
    hash = "sha256-S6VMuzOmv6dfq+W0BtSFifjEnDLy+vbqO0e4iS9MOik=";
  };

  postPatch = ''
    cp ${fetchurl {
      url = "https://raw.githubusercontent.com/localstack/aws-cdk-local/004cb3da70381a3b55465987a06b655edb687a9f/package-lock.json";
      hash = "sha256-qLKh2WNPqw5LSt5uYmwGmJHO6DlVUfI8UIof8TzuxE8=";
    }} package-lock.json
  '';

  npmDepsHash = "sha256-00ZCgjFaErYXOBV3Xqp0Fb9sz4EFgPZjmCsJ6WJ576Q=";
  nodejs = nodejs_24;

  dontNpmBuild = true;
}
