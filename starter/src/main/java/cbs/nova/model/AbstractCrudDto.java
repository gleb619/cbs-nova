package cbs.nova.model;

public interface AbstractCrudDto<T> {

  T getId();

  interface AbstractCreateDto {}

  interface AbstractUpdateDto {}
}
