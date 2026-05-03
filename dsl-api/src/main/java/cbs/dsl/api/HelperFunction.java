package cbs.dsl.api;

public interface HelperFunction<I extends HelperInput, O extends HelperOutput> {
  O execute(I input);
}
