# PRISMS JSON Validation #

PRISMS contains a framework for validating JSON according to a schema.  The functionality is very similar to XSD for XML.  JSON validation is useful if, for instance, you are exposing a JSON-based web service.  When updating the serialization code for the web service, it is easy to break the de-facto schema that existed when the service was published.  This will cause any clients using the service to break.  If the service is schematized, any schema breakage will more likely be caught during unit testing, so the developer can revise the changes to keep the schema intact.  If the schema must change, at least it will be known immediately so clients can be notified and new client implementations distributed.

# Creating a Validator #

The base class for JSON validation in PRISMS is prisms.util.json.JsonSchemaParser.  The parseSchema(String, URL) may be addressed at a JSON schema file, which will be parsed into an instance of prisms.util.json.JsonElement.  The `validate(Object) throws JsonSchemaException` and `float doesValidate(Object)` methods may be used on this object to validate JSON data (using the json-simple implementation).

# JSON Schema Language #

Details on the schema language to follow.