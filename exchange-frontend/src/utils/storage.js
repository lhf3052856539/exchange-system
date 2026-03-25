// src/utils/storage.js
const STORAGE_PREFIX = 'exchange_'

export function set(key, value) {
  try {
   const serializedValue = JSON.stringify(value)
    localStorage.setItem(`${STORAGE_PREFIX}${key}`, serializedValue)
  } catch(error) {
   console.error('Storage set error:', error)
  }
}

export function get(key, defaultValue = null) {
  try {
   const item = localStorage.getItem(`${STORAGE_PREFIX}${key}`)
   return item ? JSON.parse(item) : defaultValue
  } catch(error) {
   console.error('Storage get error:', error)
   return defaultValue
  }
}

export function remove(key) {
  try {
    localStorage.removeItem(`${STORAGE_PREFIX}${key}`)
  } catch (error) {
   console.error('Storage remove error:', error)
  }
}

export function clear() {
  try {
    Object.keys(localStorage)
      .filter(key => key.startsWith(STORAGE_PREFIX))
      .forEach(key => localStorage.removeItem(key))
  } catch (error) {
   console.error('Storage clear error:', error)
  }
}

export function has(key) {
  return localStorage.getItem(`${STORAGE_PREFIX}${key}`) !== null
}
