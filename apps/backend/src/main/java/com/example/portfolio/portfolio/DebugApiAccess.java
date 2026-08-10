package com.example.portfolio.portfolio;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local-fixture", "test"})
final class DebugApiAccess {}
