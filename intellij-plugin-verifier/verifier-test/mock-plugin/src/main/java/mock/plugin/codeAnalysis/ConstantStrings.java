package mock.plugin.codeAnalysis;

@SuppressWarnings("unused")
public class ConstantStrings {

  private static final String FINAL_STATIC_INIT_CONSTANT;

  public static final String STATIC_CONSTANT = "I_am_constant";

  public static final String STATIC_FIELD = "StaticFieldValue";

  public static final String STATIC_FIELD_CONCATENATED = "StaticField" + "Concatenated";

  private static final ConstantStrings INSTANCE = new ConstantStrings();

  static {
    FINAL_STATIC_INIT_CONSTANT = "staticInitConstant";
  }

  public String constantFunctionReturn() {
    return "ConstantFunctionValue";
  }

  public String staticFieldFunctionReturn() {
    return STATIC_FIELD;
  }

  public String staticFieldConcatenatedReturn() {
    return STATIC_FIELD_CONCATENATED;
  }

  public String recursiveString(int x) {
    if (x <= 0) {
      return "a";
    }
    return recursiveString(x - 1) + "a";
  }

  final public String myFunction() {
    return ".constantValue";
  }

  private String myRefFunction() {
    return myFunction();
  }

  public String staticConstant() {
    return STATIC_CONSTANT;
  }

  public String finalStaticInitConstant() {
    return FINAL_STATIC_INIT_CONSTANT;
  }

  public String concat() {
    return myFunction() + "Concat";
  }

  public String concat2() {
    return "prefix" + myFunction() + myRefFunction();
  }

  public String instance() {
    return INSTANCE.myRefFunction();
  }

  @SuppressWarnings("InfiniteRecursion")
  final public String directRecursion() {
    return directRecursion();
  }

}
