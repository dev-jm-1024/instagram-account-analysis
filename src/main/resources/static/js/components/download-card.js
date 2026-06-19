/* download-card.js — 플랫폼별 다운로드 카드 컴포넌트 */
window.DownloadCard = {
  name: 'DownloadCard',
  props: {
    item: { type: Object, required: true },
  },
  data() {
    return { copied: false };
  },
  methods: {
    handleDownload(event, option) {
      if (!option.href || option.href === '#') {
        event.preventDefault();
      }
    },
    copyCommand() {
      if (!this.item.command) return;
      navigator.clipboard
        .writeText(this.item.command)
        .then(() => {
          this.copied = true;
          setTimeout(() => {
            this.copied = false;
          }, 1500);
        })
        .catch(() => {
          /* 클립보드 권한 거부 시 무시 */
        });
    },
  },
  template: `
    <article class="dl-card" role="tabpanel" tabindex="0">
      <div class="dl-card__head">
        <div class="dl-card__icon-frame">
          <img class="dl-card__icon" :src="item.icon" :alt="item.name" />
        </div>
        <div>
          <h2 class="dl-card__name">{{ item.name }}</h2>
          <p class="dl-card__desc">{{ item.desc }}</p>
        </div>
      </div>

      <div class="dl-card__meta">
        <span>{{ item.version }}</span>
        <span>{{ item.size }}</span>
      </div>

      <div class="dl-card__options">
        <a
          v-for="opt in item.options"
          :key="opt.label"
          class="dl-btn"
          :class="{ 'dl-btn--primary': opt.primary }"
          :href="opt.href"
          :download="opt.fileName || null"
          @click="handleDownload($event, opt)"
        >
          <span class="dl-btn__label">{{ opt.label }}</span>
          <span class="dl-btn__sub">{{ opt.sub }}</span>
        </a>

        <div v-if="item.command" class="dl-cmd">
          <code class="dl-cmd__code">{{ item.command }}</code>
          <button class="dl-cmd__copy" type="button" @click="copyCommand">
            {{ copied ? '복사됨' : '복사' }}
          </button>
        </div>
      </div>
    </article>
  `,
};
