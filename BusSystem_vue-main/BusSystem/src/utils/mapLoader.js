// src/utils/mapLoader.js
// 百度地图API加载器

export function loadBaiduMap(ak) {
  return new Promise((resolve, reject) => {
    if (window.BMapGL) {
      resolve(window.BMapGL)
      return
    }

    window.onBMapCallback = function () {
      resolve(window.BMapGL)
    }

    const script = document.createElement('script')
    script.type = 'text/javascript'
    script.src = `https://api.map.baidu.com/api?v=1.0&type=webgl&ak=${ak}&callback=onBMapCallback`
    script.onerror = reject
    document.head.appendChild(script)
  })
}