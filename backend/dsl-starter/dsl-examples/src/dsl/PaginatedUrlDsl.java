import cbs.nova.dslexamples.v1.PaginatedUrlModels.*;
import cbs.nova.starter.helper.model.QueryStringIn;
import cbs.nova.starter.helper.model.QueryStringOut;
import cbs.nova.starter.helper.model.UrlEncodeIn;
import cbs.nova.starter.helper.model.UrlEncodeOut;

List<DslObject> define() {
  return Dsl.process("PaginatedUrl")
      .input(PaginatedUrlIn.class)
      .output(PaginatedUrlOut.class)
      .execute(ctx -> {
        PaginatedUrlIn in = ctx.body();

        var encoded = ctx.runHelper("urlEncode",
            new UrlEncodeIn(in.pathSegment(), null, false));
        if (!encoded.isSuccess()) {
          return Result.failure(encoded.cause());
        }
        String encodedPath = encoded.as(UrlEncodeOut.class).result();

        var query = ctx.runHelper("queryString",
            new QueryStringIn("build", in.queryParams(), null));
        if (!query.isSuccess()) {
          return Result.failure(query.cause());
        }
        String queryString = (String) query.as(QueryStringOut.class).result();

        StringBuilder url = new StringBuilder(in.baseUrl());
        if (!in.baseUrl().endsWith("/") && !encodedPath.startsWith("/")) {
          url.append('/');
        }
        url.append(encodedPath);
        if (!queryString.isEmpty()) {
          url.append('?').append(queryString);
        }

        return Result.success(new PaginatedUrlOut(url.toString(), encodedPath, queryString));
      })
      .buildList();
}
