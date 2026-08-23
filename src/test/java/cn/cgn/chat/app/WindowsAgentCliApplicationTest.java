package cn.cgn.chat.app;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 验证命令行工具状态清理在尚未启动计时器时也不会抛出空指针异常。
 */
class WindowsAgentCliApplicationTest {

    /**
     * 验证首次清理和重复清理都能安全执行。
     */
    @Test
    void stopToolStatusShouldBeSafeBeforeStart() throws Exception {
        Method stopToolStatus = WindowsAgentCliApplication.class
                .getDeclaredMethod("stopToolStatus", String.class);
        stopToolStatus.setAccessible(true);

        assertDoesNotThrow(() -> invokeStop(stopToolStatus));
        assertDoesNotThrow(() -> invokeStop(stopToolStatus));
    }

    private static void invokeStop(Method stopToolStatus) throws InvocationTargetException,
            IllegalAccessException {
        stopToolStatus.invoke(null, new Object[]{null});
    }
}
