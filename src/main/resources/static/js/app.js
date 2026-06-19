/* app.js — Vue 앱 부트스트랩 (컴포넌트 등록 + 마운트) */
const { createApp } = Vue;

createApp({
  data() {
    return {
      downloads: window.IA_DOWNLOADS,
      activeDownloadId: window.IA_DOWNLOADS[0]?.id,
    };
  },
  computed: {
    selectedDownload() {
      return this.downloads.find((item) => item.id === this.activeDownloadId);
    },
  },
  methods: {
    selectDownload(id) {
      this.activeDownloadId = id;
    },
  },
  components: {
    PageHeader: window.PageHeader,
    DownloadCard: window.DownloadCard,
  },
}).mount('#app');
