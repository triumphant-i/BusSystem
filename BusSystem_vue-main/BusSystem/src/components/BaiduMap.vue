<template>
  <div id="map-container" class="map-view"></div>
</template>

<script setup>
import { onMounted } from 'vue';

const props = defineProps({
  ak: { type: String, required: true }
});

const emit = defineEmits(['map-loaded']);

const initMap = () => {
  if (window.BMap) {
    const map = new window.BMap.Map("map-container");
    // 初始化地图，设置中心点坐标和地图级别 (默认为北京，可改为你需要显示的城市)
    const point = new window.BMap.Point(116.404, 39.915);
    map.centerAndZoom(point, 12);
    map.enableScrollWheelZoom(true);
    emit('map-loaded', map);
  } else {
    console.error("Baidu Map script failed to load.");
  }
};

onMounted(() => {
  if (!window.BMap) {
    const script = document.createElement("script");
    // 注意：callback=initBMap 是回调函数名
    script.src = `https://api.map.baidu.com/api?v=3.0&ak=${props.ak}&callback=initBMap`;
    script.onerror = () => console.error("Map load error");
    document.head.appendChild(script);

    window.initBMap = () => {
      initMap();
    };
  } else {
    initMap();
  }
});
</script>

<style scoped>
.map-view {
  width: 100%;
  height: 100%;
  border-radius: 8px;
}
</style>