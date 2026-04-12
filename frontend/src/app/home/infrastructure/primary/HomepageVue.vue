<template>
  <div id="app">
    <img
      alt="Vue logo"
      src="../../../../content/images/VueLogo.png"
    />
    <br />
    <h1>{{ appName }}: Vue + TypeScript + Vite</h1>
    <h2>{{ $t('home.translationEnabled') }}</h2>

    <p>
      <a
        href="https://vite.dev/guide/features.html"
        target="_blank"
        rel="noopener"
      > Vite Documentation </a>
      |
      <a
        href="https://vuejs.org/"
        target="_blank"
        rel="noopener"
      >Vue Documentation</a>
    </p>

    <p>
      Edit
      <code>src/app/home/infrastructure/primary/HomepageVue.vue</code> to test hot module replacement.
    </p>

    <p>
      <router-link to="/settings">Settings</router-link>
    </p>
  </div>
</template>

<script lang="ts">
export default {
  name: 'HomepageVue',
  data: () => {
    return {
      appName: 'Dummy',
    };
  },
};
</script>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  /*margin-top: 60px;*/
}

a {
  color: #42b983;
}

label {
  margin: 0 0.5em;
  font-weight: bold;
}

code {
  background-color: #eee;
  padding: 2px 4px;
  border-radius: 4px;
  color: #304455;
}
</style>
