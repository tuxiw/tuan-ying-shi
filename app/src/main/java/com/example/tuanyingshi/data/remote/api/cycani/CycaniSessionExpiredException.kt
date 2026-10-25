package com.example.tuanyingshi.data.remote.api.cycani

/**
 * 次元城登录态失效：源站明确拒绝当前 token（HTTP 401/403，或包体返回登录类错误码）。
 *
 * 单独定义异常类型，让上层（播放器 / 详情页）能把「登录失效」与「普通解析失败」区分开来：
 * 前者提示用户重新登录，后者才走「换线路 / 换数据源」。
 */
class CycaniSessionExpiredException(
    message: String = "次元城登录已失效，请重新登录",
) : IllegalStateException(message)
