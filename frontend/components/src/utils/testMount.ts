import { type MountingOptions, mount } from '@vue/test-utils'
import type { Component } from 'vue'

export function mountTyped<Props extends object = object>(
  component: Component,
  options: { props: Props } & Omit<MountingOptions<Props>, 'props'>,
) {
  return mount(component as any, options as any)
}
