<template>
  <div ref="canvas" class="bpmn-canvas" />
</template>

<script lang="ts">
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import { defineComponent, onMounted, ref } from 'vue';

export default defineComponent({
  name: 'BpmnViewer',
  props: {
    xml: { type: String, required: true },
  },
  setup(props) {
    const canvas = ref<HTMLElement | null>(null);

    onMounted(async () => {
      try {
        const viewer = new NavigatedViewer({ container: canvas.value! });
        await viewer.importXML(props.xml);
        viewer.get<any>('canvas').zoom('fit-viewport');
      } catch {
        // Silently ignore import errors — parent handles error display
      }
    });

    return { canvas };
  },
});
</script>

<style scoped>
.bpmn-canvas {
  height: 500px;
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
}
</style>
