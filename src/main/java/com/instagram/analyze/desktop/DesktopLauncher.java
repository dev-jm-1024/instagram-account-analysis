package com.instagram.analyze.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 데스크톱 배포(jpackage) 전용 런처. {@code desktop} 프로파일에서만 활성화된다.
 * 앱이 준비되면 (1) 기본 브라우저로 UI 를 자동으로 열고 (2) 시스템 트레이 아이콘을 띄워
 * 일반 사용자가 터미널 없이 켜고(아이콘 더블클릭) 끌(트레이 → 종료) 수 있게 한다.
 *
 * 헤드리스 환경(디스플레이 없음/CI)에서는 isSupported 가드로 모두 no-op 이 되어 안전하다.
 */
@Component
@Profile("desktop")
public class DesktopLauncher implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(DesktopLauncher.class);

    private final String port;

    public DesktopLauncher(@Value("${server.port:8080}") String port) {
        this.port = port;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String url = "http://localhost:" + port;
        openBrowser(url);
        installTray(url, event.getApplicationContext());
        log.info("데스크톱 모드 시작 — {} (트레이 아이콘에서 종료)", url);
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            log.warn("브라우저 자동 열기 실패: {}", e.getMessage());
        }
    }

    private void installTray(String url, ConfigurableApplicationContext ctx) {
        if (!SystemTray.isSupported()) {
            return;
        }
        try {
            PopupMenu menu = new PopupMenu();

            MenuItem open = new MenuItem("브라우저 열기");
            open.addActionListener(e -> openBrowser(url));

            MenuItem quit = new MenuItem("종료");
            quit.addActionListener(e -> shutdown(ctx));

            menu.add(open);
            menu.addSeparator();
            menu.add(quit);

            TrayIcon icon = new TrayIcon(trayImage(), "Instagram Analyzer", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> openBrowser(url)); // 아이콘 더블클릭 → 브라우저
            SystemTray.getSystemTray().add(icon);
        } catch (Exception e) {
            log.warn("트레이 아이콘 설치 실패: {}", e.getMessage());
        }
    }

    private void shutdown(ConfigurableApplicationContext ctx) {
        log.info("사용자 종료 요청 — 셧다운");
        try {
            ctx.close();
        } catch (Exception ignored) {
            // 컨텍스트 종료 중 예외는 무시하고 프로세스 종료로 마무리
        }
        System.exit(0);
    }

    /** 인스타그램풍 그라데이션 카메라 아이콘을 프로그램적으로 생성(리소스 의존 제거). */
    private Image trayImage() {
        int s = 64;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0xF5, 0x8D, 0x2E), s, s, new Color(0xC1, 0x3A, 0x8E)));
        g.fillRoundRect(6, 6, s - 12, s - 12, 18, 18);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(4f));
        g.drawOval(20, 20, s - 40, s - 40);
        g.fillOval(s - 26, 18, 8, 8);
        g.dispose();
        return img;
    }
}
